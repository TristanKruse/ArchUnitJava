package dev.archunitjava.metrics;

import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaCodeAccess;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberKind;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeKind;
import dev.archunitjava.model.JvmReferenceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Computes LCOM only from retained instance-member bytecode evidence, never from target loading. */
public final class CohesionMetricAnalyzer {
    public CohesionMetricReport analyze(Collection<JavaType> types) {
        return analyze(types, CohesionMetricOptions.defaults());
    }

    public CohesionMetricReport analyze(
            Collection<JavaType> types, CohesionMetricOptions options) {
        CohesionMetricOptions policy = Objects.requireNonNull(options, "options");
        List<CohesionValue> values = new ArrayList<>();
        Objects.requireNonNull(types, "types").stream()
                .map(value -> Objects.requireNonNull(value, "type"))
                .sorted()
                .forEach(type -> values.addAll(type(type, policy)));
        return CohesionMetricReport.of(values);
    }

    private static List<CohesionValue> type(JavaType type, CohesionMetricOptions options) {
        TypeId subject = TypeId.ofBinaryName(type.binaryName());
        if (type.kind() != JavaTypeKind.CLASS && type.kind() != JavaTypeKind.RECORD) {
            return java.util.Arrays.stream(LcomVariant.values())
                    .map(variant -> unavailable(
                            subject, variant, MetricAvailability.NOT_APPLICABLE, 0, 0,
                            "LCOM is defined here only for classes and records"))
                    .toList();
        }
        List<JavaMember> methods = type.declaredMembers().stream()
                .filter(member -> member.kind() == JavaMemberKind.METHOD)
                .filter(member -> !member.modifiers().contains(JavaMemberModifier.STATIC))
                .filter(member -> included(member, options))
                .toList();
        List<JavaMember> fields = type.declaredMembers().stream()
                .filter(member -> member.kind() == JavaMemberKind.FIELD)
                .filter(member -> !member.modifiers().contains(JavaMemberModifier.STATIC))
                .filter(member -> included(member, options))
                .toList();
        int methodCount = methods.size();
        int fieldCount = fields.size();
        boolean complete = methods.stream().allMatch(JavaMember::hasCode);
        AccessIndex access = complete ? accesses(type, methods, fields) : AccessIndex.empty();
        List<CohesionValue> result = new ArrayList<>();

        if (methodCount < 2 || fieldCount == 0) {
            result.add(unavailable(subject, LcomVariant.CK_LCOM1,
                    MetricAvailability.NOT_APPLICABLE, methodCount, fieldCount,
                    "CK LCOM1 requires at least two instance methods and one instance field"));
            result.add(unavailable(subject, LcomVariant.HENDERSON_SELLERS,
                    MetricAvailability.NOT_APPLICABLE, methodCount, fieldCount,
                    "Henderson-Sellers LCOM requires M >= 2 and F >= 1"));
        } else if (!complete) {
            result.add(unavailable(subject, LcomVariant.CK_LCOM1,
                    MetricAvailability.INCOMPLETE_EVIDENCE, methodCount, fieldCount,
                    "At least one eligible method has no bytecode access evidence"));
            result.add(unavailable(subject, LcomVariant.HENDERSON_SELLERS,
                    MetricAvailability.INCOMPLETE_EVIDENCE, methodCount, fieldCount,
                    "At least one eligible method has no bytecode access evidence"));
        } else {
            result.add(computed(subject, LcomVariant.CK_LCOM1,
                    MetricAmount.of(lcom1(methods, access.fieldsByMethod), MetricUnit.METHOD_PAIRS),
                    methodCount, fieldCount,
                    "max(disjoint method pairs - field-sharing pairs, 0)"));
            result.add(computed(subject, LcomVariant.HENDERSON_SELLERS,
                    MetricAmount.of(hendersonSellers(methods, fields, access.fieldsByMethod),
                            MetricUnit.RATIO),
                    methodCount, fieldCount,
                    "min(1, max(0, (M - average methods per field) / (M - 1)))"));
        }

        if (methodCount == 0) {
            result.add(unavailable(subject, LcomVariant.LCOM4,
                    MetricAvailability.NOT_APPLICABLE, methodCount, fieldCount,
                    "LCOM4 requires at least one instance method"));
        } else if (!complete) {
            result.add(unavailable(subject, LcomVariant.LCOM4,
                    MetricAvailability.INCOMPLETE_EVIDENCE, methodCount, fieldCount,
                    "At least one eligible method has no bytecode access evidence"));
        } else {
            result.add(computed(subject, LcomVariant.LCOM4,
                    MetricAmount.of(lcom4(methods, access), MetricUnit.COHESION_COMPONENTS),
                    methodCount, fieldCount,
                    "connected method components joined by shared fields or direct self calls"));
        }
        return result;
    }

    private static AccessIndex accesses(
            JavaType type, List<JavaMember> methods, List<JavaMember> fields) {
        Set<String> fieldKeys = fields.stream().map(CohesionMetricAnalyzer::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, JavaMember> methodsByKey = new TreeMap<>();
        methods.forEach(method -> methodsByKey.put(key(method), method));
        Map<JavaMember, Set<String>> fieldsByMethod = new HashMap<>();
        Map<JavaMember, Set<JavaMember>> callsByMethod = new HashMap<>();
        for (JavaMember method : methods) {
            Set<String> accessedFields = new TreeSet<>();
            Set<JavaMember> calledMethods = new TreeSet<>();
            for (JavaCodeAccess access : method.codeAccesses()) {
                if (!(access.target().ownerType() instanceof JvmReferenceType owner)
                        || !owner.binaryName().equals(type.binaryName())) continue;
                String targetKey = key(access.target().name(), access.target().descriptor());
                if (!access.target().method() && fieldKeys.contains(targetKey)) {
                    accessedFields.add(targetKey);
                } else if (access.target().method()) {
                    Optional.ofNullable(methodsByKey.get(targetKey)).ifPresent(calledMethods::add);
                }
            }
            fieldsByMethod.put(method, Set.copyOf(accessedFields));
            callsByMethod.put(method, Set.copyOf(calledMethods));
        }
        return new AccessIndex(Map.copyOf(fieldsByMethod), Map.copyOf(callsByMethod));
    }

    private static long lcom1(List<JavaMember> methods, Map<JavaMember, Set<String>> fields) {
        long sharing = 0;
        long disjoint = 0;
        for (int left = 0; left < methods.size(); left++) {
            for (int right = left + 1; right < methods.size(); right++) {
                Set<String> intersection = new HashSet<>(fields.get(methods.get(left)));
                intersection.retainAll(fields.get(methods.get(right)));
                if (intersection.isEmpty()) disjoint++;
                else sharing++;
            }
        }
        return Math.max(disjoint - sharing, 0);
    }

    private static double hendersonSellers(
            List<JavaMember> methods,
            List<JavaMember> fields,
            Map<JavaMember, Set<String>> fieldsByMethod) {
        long accessCount = 0;
        for (JavaMember field : fields) {
            String fieldKey = key(field);
            accessCount += methods.stream()
                    .filter(method -> fieldsByMethod.get(method).contains(fieldKey)).count();
        }
        double average = (double) accessCount / fields.size();
        double value = (methods.size() - average) / (methods.size() - 1);
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static int lcom4(List<JavaMember> methods, AccessIndex access) {
        Map<JavaMember, JavaMember> parent = new HashMap<>();
        methods.forEach(method -> parent.put(method, method));
        for (int left = 0; left < methods.size(); left++) {
            for (int right = left + 1; right < methods.size(); right++) {
                Set<String> intersection = new HashSet<>(access.fieldsByMethod.get(methods.get(left)));
                intersection.retainAll(access.fieldsByMethod.get(methods.get(right)));
                if (!intersection.isEmpty()) union(parent, methods.get(left), methods.get(right));
            }
        }
        access.callsByMethod.forEach((origin, targets) ->
                targets.forEach(target -> union(parent, origin, target)));
        return (int) methods.stream().map(method -> root(parent, method)).distinct().count();
    }

    private static void union(
            Map<JavaMember, JavaMember> parent, JavaMember left, JavaMember right) {
        JavaMember leftRoot = root(parent, left);
        JavaMember rightRoot = root(parent, right);
        if (leftRoot.equals(rightRoot)) return;
        if (leftRoot.compareTo(rightRoot) < 0) parent.put(rightRoot, leftRoot);
        else parent.put(leftRoot, rightRoot);
    }

    private static JavaMember root(Map<JavaMember, JavaMember> parent, JavaMember value) {
        JavaMember current = value;
        while (!parent.get(current).equals(current)) current = parent.get(current);
        return current;
    }

    private static boolean included(JavaMember member, CohesionMetricOptions options) {
        return options.includeSyntheticMembers()
                || !member.modifiers().contains(JavaMemberModifier.SYNTHETIC)
                        && !member.modifiers().contains(JavaMemberModifier.BRIDGE);
    }

    private static String key(JavaMember member) {
        return key(member.name(), member.descriptor());
    }

    private static String key(String name, String descriptor) {
        return name + '\u0000' + descriptor;
    }

    private static CohesionValue computed(
            TypeId subject,
            LcomVariant variant,
            MetricAmount amount,
            int methods,
            int fields,
            String explanation) {
        return new CohesionValue(subject, variant, MetricAvailability.COMPUTED,
                Optional.of(amount), methods, fields, explanation);
    }

    private static CohesionValue unavailable(
            TypeId subject,
            LcomVariant variant,
            MetricAvailability availability,
            int methods,
            int fields,
            String explanation) {
        return new CohesionValue(subject, variant, availability,
                Optional.empty(), methods, fields, explanation);
    }

    private record AccessIndex(
            Map<JavaMember, Set<String>> fieldsByMethod,
            Map<JavaMember, Set<JavaMember>> callsByMethod) {
        private static AccessIndex empty() {
            return new AccessIndex(Map.of(), Map.of());
        }
    }
}
