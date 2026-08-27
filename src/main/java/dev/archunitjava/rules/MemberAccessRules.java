package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaCodeAccess;
import dev.archunitjava.model.JavaCodeAccessTarget;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaMemberSignature;
import dev.archunitjava.model.JavaModifier;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.MemberSelection;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.SelectorDescription;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Policies over exact bytecode member references without runtime dispatch inference. */
public final class MemberAccessRules {
    private MemberAccessRules() {}

    public static ArchitectureRule accesses(
            TypeModelResult model,
            MemberSelector callers,
            MemberSelector targets,
            MemberAccessRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        MemberSelector callerSelector = Objects.requireNonNull(callers, "callers");
        MemberSelector targetSelector = Objects.requireNonNull(targets, "targets");
        MemberAccessRuleSpec ruleSpec = Objects.requireNonNull(spec, "spec");
        MemberSelection callerSelection = callerSelector.selectFrom(model);
        MemberSelection targetSelection = targetSelector.selectFrom(model);
        Map<String, JavaType> types = indexedTypes(model.types());
        List<JavaMember> selectedCallers = callerSelection.selected().stream()
                .filter(JavaMember::isCodeUnit)
                .filter(caller -> ruleSpec.compilerAccesses() == CompilerAccessPolicy.INCLUDE
                        || !compilerCreated(caller, types))
                .toList();
        Domain domain = new Domain(
                callerSelector.description(),
                targetSelector.description(),
                selectedCallers,
                signatures(targetSelection.selected()),
                declarationEvidence(selectedCallers));
        return rule(domain, ruleSpec);
    }

    private static ArchitectureRule rule(Domain domain, MemberAccessRuleSpec spec) {
        String identity = RuleIdentities.semantic(
                "member-access",
                domain.callerDescription.text(),
                domain.targetDescription.text(),
                spec.semanticKey());
        String description = domain.callerDescription + " " + spec.mode() + " "
                + spec.accessKinds() + " accesses to " + domain.targetDescription;
        return ArchitectureRules.define(identity, description,
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        List.of(
                                new RuleSelection(
                                        "callers", domain.callerDescription, domain.callers.size()),
                                new RuleSelection(
                                        "targets", domain.targetDescription, domain.targets.size())),
                        terminalDiagnostics -> evaluate(
                                metadata, domain, spec, terminalDiagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            Domain domain,
            MemberAccessRuleSpec spec,
            List<Diagnostic> diagnostics) {
        TreeMap<JavaMemberSignature, List<JavaCodeAccess>> accesses = new TreeMap<>();
        for (JavaMember caller : domain.callers) {
            accesses.put(caller.signature(), caller.codeAccesses().stream()
                    .filter(access -> spec.accessKinds().contains(access.kind()))
                    .filter(access -> spec.compilerAccesses() == CompilerAccessPolicy.INCLUDE
                            || !access.artifactProvenance().compilerCreated())
                    .toList());
        }

        List<Violation> violations = switch (spec.mode()) {
            case NO -> edgeViolations(metadata, domain, accesses, true);
            case ONLY -> edgeViolations(metadata, domain, accesses, false);
            case ANY -> anyViolation(metadata, domain, accesses, spec);
            case REQUIRED -> requiredViolations(metadata, domain, accesses, spec);
        };
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static List<Violation> edgeViolations(
            RuleMetadata metadata,
            Domain domain,
            Map<JavaMemberSignature, List<JavaCodeAccess>> accesses,
            boolean matchingAccessesViolate) {
        List<Violation> violations = new ArrayList<>();
        accesses.values().stream().flatMap(Collection::stream).forEach(access -> {
            boolean matches = matches(access.target(), domain.targets);
            if (matches == matchingAccessesViolate) {
                violations.add(accessViolation(
                        metadata,
                        access,
                        matchingAccessesViolate
                                ? "member-access.forbidden"
                                : "member-access.outside-only"));
            }
        });
        return violations.stream().sorted().toList();
    }

    private static List<Violation> anyViolation(
            RuleMetadata metadata,
            Domain domain,
            Map<JavaMemberSignature, List<JavaCodeAccess>> accesses,
            MemberAccessRuleSpec spec) {
        boolean found = accesses.values().stream().flatMap(Collection::stream)
                .anyMatch(access -> matches(access.target(), domain.targets));
        if (found || domain.callers.isEmpty()) return List.of();
        return List.of(missingViolation(
                metadata, domain, domain.callers.getFirst(), spec, "member-access.any-required"));
    }

    private static List<Violation> requiredViolations(
            RuleMetadata metadata,
            Domain domain,
            Map<JavaMemberSignature, List<JavaCodeAccess>> accesses,
            MemberAccessRuleSpec spec) {
        return domain.callers.stream()
                .filter(caller -> accesses.get(caller.signature()).stream()
                        .noneMatch(access -> matches(access.target(), domain.targets)))
                .map(caller -> missingViolation(
                        metadata, domain, caller, spec, "member-access.required"))
                .sorted()
                .toList();
    }

    private static boolean matches(
            JavaCodeAccessTarget target, Set<JavaMemberSignature> selectedTargets) {
        if (!(target.ownerType() instanceof JvmReferenceType owner)) return false;
        return selectedTargets.contains(new JavaMemberSignature(
                new dev.archunitjava.model.JavaTypeName(owner.binaryName()),
                target.name(), target.descriptor()));
    }

    private static Violation accessViolation(
            RuleMetadata metadata, JavaCodeAccess access, String code) {
        MemberId caller = memberId(access.caller());
        SymbolicMemberTargetId target = targetId(access.target());
        TreeMap<String, String> attributes = new TreeMap<>();
        attributes.put("accessKind", access.kind().name());
        attributes.put("bytecodeOffset", Integer.toString(access.location().bytecodeOffset()));
        attributes.put("caller", access.caller().stableKey());
        attributes.put("compilerCreated",
                Boolean.toString(access.artifactProvenance().compilerCreated()));
        attributes.put("interfaceTarget", Boolean.toString(access.interfaceTarget()));
        attributes.put("lineNumber", access.location().lineNumber().isPresent()
                ? Integer.toString(access.location().lineNumber().getAsInt()) : "<unknown>");
        attributes.put("opcode", access.opcode().name());
        attributes.put("resolution", "SYMBOLIC_CONSTANT_POOL_TARGET");
        attributes.put("runtimeDispatch", "NOT_RESOLVED");
        attributes.put("sourceFile", access.location().sourceFile()
                .map(value -> value.value()).orElse("<unknown>"));
        attributes.put("targetDescriptor", access.target().descriptor());
        attributes.put("targetName", access.target().name());
        attributes.put("targetOwner", access.target().ownerType().displayName());
        attributes.put("targetSignature", target.stableKey());
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code,
                        caller.stableKey(), target.stableKey(),
                        Integer.toString(access.location().bytecodeOffset()),
                        access.opcode().name())),
                code,
                metadata.severity(),
                List.of(
                        new ViolationSubject("caller", caller),
                        new ViolationSubject("symbolicTarget", target)),
                List.of(access.location().dependencyEvidence(caller)),
                attributes);
    }

    private static Violation missingViolation(
            RuleMetadata metadata,
            Domain domain,
            JavaMember caller,
            MemberAccessRuleSpec spec,
            String code) {
        MemberId id = memberId(caller.signature());
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code, id.stableKey())),
                code,
                metadata.severity(),
                List.of(new ViolationSubject("caller", id)),
                List.of(domain.declarations.get(caller.signature())),
                Map.of(
                        "accessKinds", spec.accessKinds().stream()
                                .map(Enum::name).sorted().toList().toString(),
                        "caller", caller.signature().stableKey(),
                        "requiredTargetSelector", domain.targetDescription.text(),
                        "resolution", "SYMBOLIC_CONSTANT_POOL_TARGET",
                        "runtimeDispatch", "NOT_RESOLVED"));
    }

    private static SymbolicMemberTargetId targetId(JavaCodeAccessTarget target) {
        return new SymbolicMemberTargetId(
                target.ownerType().descriptor(), target.name(), target.descriptor());
    }

    private static Set<JavaMemberSignature> signatures(Collection<JavaMember> members) {
        return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(
                members.stream().map(JavaMember::signature).toList()));
    }

    private static Map<JavaMemberSignature, DependencyEvidence> declarationEvidence(
            Collection<JavaMember> members) {
        TreeMap<JavaMemberSignature, DependencyEvidence> result = new TreeMap<>();
        members.forEach(member -> result.put(
                member.signature(),
                DependencyEvidence.at(member.location().resource().locationId())));
        return Map.copyOf(result);
    }

    private static Map<String, JavaType> indexedTypes(Collection<JavaType> types) {
        TreeMap<String, JavaType> result = new TreeMap<>();
        types.stream().sorted().forEach(type -> result.putIfAbsent(type.binaryName(), type));
        return Map.copyOf(result);
    }

    private static boolean compilerCreated(
            JavaMember member, Map<String, JavaType> types) {
        JavaType owner = types.get(member.owner().binaryName());
        return member.modifiers().contains(JavaMemberModifier.SYNTHETIC)
                || member.modifiers().contains(JavaMemberModifier.BRIDGE)
                || owner != null && owner.modifiers().contains(JavaModifier.SYNTHETIC);
    }

    private static MemberId memberId(JavaMemberSignature signature) {
        return MemberId.of(
                TypeId.ofBinaryName(signature.owner().binaryName()),
                signature.name(), signature.descriptor());
    }

    private record Domain(
            SelectorDescription callerDescription,
            SelectorDescription targetDescription,
            List<JavaMember> callers,
            Set<JavaMemberSignature> targets,
            Map<JavaMemberSignature, DependencyEvidence> declarations) {
        private Domain {
            Objects.requireNonNull(callerDescription, "callerDescription");
            Objects.requireNonNull(targetDescription, "targetDescription");
            callers = callers.stream().distinct().sorted().toList();
            targets = java.util.Collections.unmodifiableSet(
                    new java.util.TreeSet<>(Objects.requireNonNull(targets, "targets")));
            declarations = Map.copyOf(Objects.requireNonNull(declarations, "declarations"));
        }
    }
}
