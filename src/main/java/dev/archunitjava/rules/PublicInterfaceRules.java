package dev.archunitjava.rules;

import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaCodeAccess;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaMemberSignature;
import dev.archunitjava.model.JavaModifier;
import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.JavaModuleIdentity;
import dev.archunitjava.model.JavaModuleKind;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.MemberSelection;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.SelectorDescription;
import dev.archunitjava.selector.TypeSelection;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Rules that keep cross-package consumers on an explicitly approved public interface. */
public final class PublicInterfaceRules {
    public static final String BLIND_SPOTS_DIAGNOSTIC = "public-interface.blind-spots";

    private PublicInterfaceRules() {}

    /**
     * Checks resolved member accesses crossing a package or module boundary. Both the target type
     * and member must be approved and public; an explicit target module must also export the
     * package to the caller.
     */
    public static ArchitectureRule onlyAccessApprovedInterfaces(
            TypeModelResult model,
            MemberSelector consumers,
            TypeSelector approvedTypes,
            MemberSelector approvedMembers) {
        Objects.requireNonNull(model, "model");
        MemberSelector consumerSelector = Objects.requireNonNull(consumers, "consumers");
        TypeSelector typeSelector = Objects.requireNonNull(approvedTypes, "approvedTypes");
        MemberSelector memberSelector = Objects.requireNonNull(approvedMembers, "approvedMembers");
        MemberSelection consumerSelection = consumerSelector.selectFrom(model);
        TypeSelection typeSelection = typeSelector.selectFrom(model);
        MemberSelection memberSelection = memberSelector.selectFrom(model);
        Domain domain = new Domain(
                consumerSelector.description(),
                typeSelector.description(),
                memberSelector.description(),
                consumerSelection.selected().stream().filter(JavaMember::isCodeUnit).toList(),
                typeSelection.selected(),
                memberSelection.selected(),
                model.types(),
                model.modules());
        return rule(domain);
    }

    private static ArchitectureRule rule(Domain domain) {
        String identity = RuleIdentities.semantic(
                "public-interface",
                domain.consumerDescription.text(),
                domain.approvedTypeDescription.text(),
                domain.approvedMemberDescription.text());
        String description = domain.consumerDescription
                + " only access approved public interface types " + domain.approvedTypeDescription
                + " and members " + domain.approvedMemberDescription;
        return ArchitectureRules.define(identity, description,
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        List.of(
                                new RuleSelection("consumers", domain.consumerDescription, domain.consumers.size()),
                                new RuleSelection("approvedTypes", domain.approvedTypeDescription,
                                        domain.approvedTypes.size()),
                                new RuleSelection("approvedMembers", domain.approvedMemberDescription,
                                        domain.approvedMembers.size())),
                        terminalDiagnostics -> evaluate(metadata, domain, terminalDiagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata, Domain domain, List<Diagnostic> terminalDiagnostics) {
        List<Violation> violations = new ArrayList<>();
        int unresolvedTargets = 0;
        for (JavaMember consumer : domain.consumers) {
            JavaType callerType = domain.types.get(consumer.owner().binaryName());
            if (callerType == null) continue;
            for (JavaCodeAccess access : consumer.codeAccesses()) {
                ResolvedTarget target = resolve(access, domain);
                if (target == null) {
                    unresolvedTargets++;
                    continue;
                }
                if (!crossesBoundary(callerType, target.type, domain)) continue;
                Assessment assessment = assess(callerType, target, domain);
                if (!assessment.allowed()) {
                    violations.add(violation(metadata, access, callerType, target, assessment, domain));
                }
            }
        }
        List<Diagnostic> diagnostics = new ArrayList<>(terminalDiagnostics);
        diagnostics.add(new Diagnostic(
                BLIND_SPOTS_DIAGNOSTIC,
                Severity.INFO,
                Map.of(
                        "reflection", "NOT_ANALYZED",
                        "runtimeAddExports", "NOT_MODELED",
                        "runtimeDispatch", "NOT_RESOLVED",
                        "scope", "IMPORTED_RESOLVED_BYTECODE_TARGETS")));
        if (unresolvedTargets > 0) {
            diagnostics.add(new Diagnostic(
                    "public-interface.unresolved-targets",
                    Severity.INFO,
                    Map.of(
                            "count", Integer.toString(unresolvedTargets),
                            "handling", "NOT_EVALUATED")));
        }
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static Assessment assess(JavaType caller, ResolvedTarget target, Domain domain) {
        boolean approvedType = domain.approvedTypes.contains(target.type.name());
        boolean approvedMember = domain.approvedMembers.contains(target.member.signature());
        boolean publicType = publiclyVisible(target.type, domain);
        boolean publicMember = target.member.modifiers().contains(JavaMemberModifier.PUBLIC);
        JavaAccessVisibility visibility = visibility(caller, target);
        ModuleExportAccess export = exportAccess(caller, target.type, domain);
        boolean exported = switch (export) {
            case PACKAGE_NOT_EXPORTED, QUALIFIED_EXPORT_TO_OTHER_MODULES -> false;
            default -> true;
        };
        return new Assessment(
                approvedType,
                approvedMember,
                publicType,
                publicMember,
                visibility,
                export,
                approvedType && approvedMember && publicType && publicMember && exported);
    }

    private static JavaAccessVisibility visibility(JavaType caller, ResolvedTarget target) {
        JavaMember member = target.member;
        if (member.modifiers().contains(JavaMemberModifier.PUBLIC)) {
            return JavaAccessVisibility.PUBLIC;
        }
        boolean samePackage = caller.packageName().equals(target.type.packageName());
        if (member.modifiers().contains(JavaMemberModifier.PROTECTED)) {
            return samePackage
                    ? JavaAccessVisibility.PROTECTED_SAME_PACKAGE
                    : JavaAccessVisibility.PROTECTED_CROSS_PACKAGE;
        }
        if (member.modifiers().contains(JavaMemberModifier.PRIVATE)) {
            return sameNest(caller, target.type)
                    ? JavaAccessVisibility.PRIVATE_NESTMATE
                    : JavaAccessVisibility.PRIVATE_INACCESSIBLE;
        }
        return samePackage
                ? JavaAccessVisibility.PACKAGE_PRIVATE
                : JavaAccessVisibility.PACKAGE_PRIVATE_INACCESSIBLE;
    }

    private static boolean sameNest(JavaType first, JavaType second) {
        return first.nesting().nestHost().equals(second.nesting().nestHost());
    }

    private static ModuleExportAccess exportAccess(
            JavaType caller, JavaType target, Domain domain) {
        Optional<JavaModule> callerModule = domain.moduleOf(caller);
        Optional<JavaModule> targetModule = domain.moduleOf(target);
        if (targetModule.isEmpty()) return ModuleExportAccess.UNNAMED_OR_UNKNOWN_MODULE;
        JavaModule targetValue = targetModule.orElseThrow();
        if (callerModule.map(JavaModule::identity).equals(Optional.of(targetValue.identity()))) {
            return ModuleExportAccess.SAME_MODULE;
        }
        if (targetValue.identity().kind() == JavaModuleKind.AUTOMATIC) {
            return ModuleExportAccess.AUTOMATIC_MODULE;
        }
        Optional<dev.archunitjava.model.JavaModulePackageDirective> directive = targetValue.exports().stream()
                .filter(value -> value.packageName().equals(target.packageName()))
                .findFirst();
        if (directive.isEmpty()) return ModuleExportAccess.PACKAGE_NOT_EXPORTED;
        if (!directive.orElseThrow().qualified()) return ModuleExportAccess.UNQUALIFIED_EXPORT;
        Optional<String> callerName = callerModule.flatMap(module -> module.identity().name());
        return callerName.filter(directive.orElseThrow().targetModules()::contains).isPresent()
                ? ModuleExportAccess.QUALIFIED_EXPORT_TO_CALLER
                : ModuleExportAccess.QUALIFIED_EXPORT_TO_OTHER_MODULES;
    }

    private static boolean crossesBoundary(JavaType caller, JavaType target, Domain domain) {
        if (!caller.packageName().equals(target.packageName())) return true;
        Optional<JavaModuleIdentity> callerModule = domain.moduleOf(caller).map(JavaModule::identity);
        Optional<JavaModuleIdentity> targetModule = domain.moduleOf(target).map(JavaModule::identity);
        return !callerModule.equals(targetModule);
    }

    private static ResolvedTarget resolve(JavaCodeAccess access, Domain domain) {
        if (!(access.target().ownerType() instanceof JvmReferenceType owner)) return null;
        JavaType type = domain.types.get(owner.binaryName());
        if (type == null) return null;
        JavaMemberSignature signature = new JavaMemberSignature(
                type.name(), access.target().name(), access.target().descriptor());
        JavaMember member = domain.members.get(signature);
        return member == null ? null : new ResolvedTarget(type, member);
    }

    private static Violation violation(
            RuleMetadata metadata,
            JavaCodeAccess access,
            JavaType callerType,
            ResolvedTarget target,
            Assessment assessment,
            Domain domain) {
        MemberId caller = memberId(access.caller());
        MemberId targetId = memberId(target.member.signature());
        TreeMap<String, String> attributes = new TreeMap<>();
        attributes.put("approvedMember", Boolean.toString(assessment.approvedMember));
        attributes.put("approvedType", Boolean.toString(assessment.approvedType));
        attributes.put("approvedEntryPoint", approvedEntryPoint(callerType, target, domain));
        attributes.put("callerPackage", callerType.packageName().value());
        attributes.put("javaAccess", assessment.visibility.name());
        attributes.put("moduleExport", assessment.exportAccess.name());
        attributes.put("nestmate", Boolean.toString(sameNest(callerType, target.type)));
        attributes.put("publicMember", Boolean.toString(assessment.publicMember));
        attributes.put("publicType", Boolean.toString(assessment.publicType));
        attributes.put("resolution", "EXACT_IMPORTED_SYMBOLIC_TARGET");
        attributes.put("runtimeDispatch", "NOT_RESOLVED");
        attributes.put("target", target.member.signature().stableKey());
        attributes.put("targetPackage", target.type.packageName().value());
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(),
                        "public-interface.boundary-bypass",
                        caller.stableKey(),
                        targetId.stableKey(),
                        Integer.toString(access.location().bytecodeOffset()))),
                "public-interface.boundary-bypass",
                metadata.severity(),
                List.of(
                        new ViolationSubject("consumer", caller),
                        new ViolationSubject("internalTarget", targetId)),
                List.of(access.location().dependencyEvidence(caller)),
                attributes);
    }

    private static String approvedEntryPoint(
            JavaType caller, ResolvedTarget target, Domain domain) {
        List<JavaMemberSignature> candidates = domain.approvedMembers.stream()
                .filter(signature -> domain.approvedTypes.contains(signature.owner()))
                .map(domain.members::get)
                .filter(Objects::nonNull)
                .filter(member -> member.modifiers().contains(JavaMemberModifier.PUBLIC))
                .filter(member -> {
                    JavaType owner = domain.types.get(member.owner().binaryName());
                    return owner != null
                            && publiclyVisible(owner, domain)
                            && switch (exportAccess(caller, owner, domain)) {
                                case PACKAGE_NOT_EXPORTED, QUALIFIED_EXPORT_TO_OTHER_MODULES -> false;
                                default -> true;
                            };
                })
                .filter(member -> member.name().equals(target.member.name()))
                .filter(member -> member.descriptor().equals(target.member.descriptor()))
                .map(JavaMember::signature)
                .sorted()
                .toList();
        return candidates.size() == 1 ? candidates.getFirst().stableKey()
                : candidates.isEmpty() ? "<none>" : "<ambiguous>";
    }

    private static boolean publiclyVisible(JavaType type, Domain domain) {
        Set<dev.archunitjava.model.JavaTypeName> visited = new TreeSet<>();
        JavaType current = type;
        while (true) {
            if (!visited.add(current.name())
                    || !current.modifiers().contains(JavaModifier.PUBLIC)) {
                return false;
            }
            Optional<dev.archunitjava.model.JavaTypeName> lexicalOwner =
                    current.nesting().lexicalOwner();
            if (lexicalOwner.isEmpty()) return true;
            current = domain.types.get(lexicalOwner.orElseThrow().binaryName());
            if (current == null) return false;
        }
    }

    private static MemberId memberId(JavaMemberSignature signature) {
        return MemberId.of(
                TypeId.ofBinaryName(signature.owner().binaryName()),
                signature.name(), signature.descriptor());
    }

    private record ResolvedTarget(JavaType type, JavaMember member) {}

    private record Assessment(
            boolean approvedType,
            boolean approvedMember,
            boolean publicType,
            boolean publicMember,
            JavaAccessVisibility visibility,
            ModuleExportAccess exportAccess,
            boolean allowed) {}

    private static final class Domain {
        private final SelectorDescription consumerDescription;
        private final SelectorDescription approvedTypeDescription;
        private final SelectorDescription approvedMemberDescription;
        private final List<JavaMember> consumers;
        private final Set<dev.archunitjava.model.JavaTypeName> approvedTypes;
        private final Set<JavaMemberSignature> approvedMembers;
        private final Map<String, JavaType> types;
        private final Map<JavaMemberSignature, JavaMember> members;
        private final Map<String, JavaModule> modulesByLocation;

        private Domain(
                SelectorDescription consumerDescription,
                SelectorDescription approvedTypeDescription,
                SelectorDescription approvedMemberDescription,
                Collection<JavaMember> consumers,
                Collection<JavaType> approvedTypes,
                Collection<JavaMember> approvedMembers,
                Collection<JavaType> types,
                Collection<JavaModule> modules) {
            this.consumerDescription = Objects.requireNonNull(consumerDescription, "consumerDescription");
            this.approvedTypeDescription = Objects.requireNonNull(approvedTypeDescription,
                    "approvedTypeDescription");
            this.approvedMemberDescription = Objects.requireNonNull(approvedMemberDescription,
                    "approvedMemberDescription");
            this.consumers = consumers.stream().distinct().sorted().toList();
            this.approvedTypes = Set.copyOf(new TreeSet<>(approvedTypes.stream()
                    .map(JavaType::name).toList()));
            this.approvedMembers = Set.copyOf(new TreeSet<>(approvedMembers.stream()
                    .map(JavaMember::signature).toList()));
            TreeMap<String, JavaType> typeIndex = new TreeMap<>();
            TreeMap<JavaMemberSignature, JavaMember> memberIndex = new TreeMap<>();
            types.stream().sorted().forEach(type -> {
                typeIndex.putIfAbsent(type.binaryName(), type);
                type.declaredMembers().forEach(member -> memberIndex.putIfAbsent(member.signature(), member));
            });
            this.types = Map.copyOf(typeIndex);
            this.members = Map.copyOf(memberIndex);
            TreeMap<String, JavaModule> moduleIndex = new TreeMap<>();
            modules.stream().sorted().forEach(module -> moduleIndex.putIfAbsent(
                    locationKey(module.location().resource()), module));
            this.modulesByLocation = Map.copyOf(moduleIndex);
        }

        private Optional<JavaModule> moduleOf(JavaType type) {
            return Optional.ofNullable(modulesByLocation.get(locationKey(type.location().resource())));
        }

        private static String locationKey(dev.archunitjava.model.ClassResourceLocation location) {
            return location.kind() + ":" + location.precedence() + ":" + location.container();
        }
    }
}
