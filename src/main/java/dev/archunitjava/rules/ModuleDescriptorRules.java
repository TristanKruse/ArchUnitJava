package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.JavaModuleKind;
import dev.archunitjava.model.JavaModulePackageDirective;
import dev.archunitjava.model.JavaModuleProvide;
import dev.archunitjava.model.JavaModuleRequire;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.ModuleSelection;
import dev.archunitjava.selector.ModuleSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** JPMS Module-attribute policies, deliberately separate from observed bytecode dependencies. */
public final class ModuleDescriptorRules {
    private ModuleDescriptorRules() {}

    public static ArchitectureRule requires(
            TypeModelResult model,
            ModuleSelector modules,
            JavaPattern targetModules,
            ModuleRequireRuleSpec spec,
            NonExplicitModulePolicy nonExplicitPolicy) {
        Domain domain = domain(model, modules, nonExplicitPolicy);
        JavaPattern targets = qualified(targetModules, "target module");
        ModuleRequireRuleSpec value = Objects.requireNonNull(spec, "spec");
        return rule(
                "requires",
                domain,
                value.mode(),
                targets.description().toString() + ":" + value,
                module -> module.requires().stream()
                        .map(require -> new Declaration(
                                require.moduleName(),
                                requiresAttributes(require),
                                targets.matches(require.moduleName())
                                        && value.transitive().matches(require.transitive())
                                        && value.staticPhase().matches(require.staticPhase())))
                        .toList());
    }

    public static ArchitectureRule exports(
            TypeModelResult model,
            ModuleSelector modules,
            JavaPattern packages,
            ModulePackageRuleSpec spec,
            NonExplicitModulePolicy nonExplicitPolicy) {
        return packageDirectives(
                "exports", model, modules, packages, spec, nonExplicitPolicy, JavaModule::exports);
    }

    public static ArchitectureRule opens(
            TypeModelResult model,
            ModuleSelector modules,
            JavaPattern packages,
            ModulePackageRuleSpec spec,
            NonExplicitModulePolicy nonExplicitPolicy) {
        return packageDirectives(
                "opens", model, modules, packages, spec, nonExplicitPolicy, JavaModule::opens);
    }

    public static ArchitectureRule uses(
            TypeModelResult model,
            ModuleSelector modules,
            JavaPattern serviceTypes,
            ModuleRuleMode mode,
            NonExplicitModulePolicy nonExplicitPolicy) {
        Domain domain = domain(model, modules, nonExplicitPolicy);
        JavaPattern services = qualified(serviceTypes, "service type");
        ModuleRuleMode value = Objects.requireNonNull(mode, "mode");
        return rule(
                "uses",
                domain,
                value,
                services.description().toString(),
                module -> module.uses().stream()
                        .map(service -> new Declaration(
                                service.binaryName(),
                                Map.of("serviceType", service.binaryName()),
                                services.matches(service.binaryName())))
                        .toList());
    }

    public static ArchitectureRule provides(
            TypeModelResult model,
            ModuleSelector modules,
            JavaPattern serviceTypes,
            JavaPattern providerTypes,
            ModuleRuleMode mode,
            NonExplicitModulePolicy nonExplicitPolicy) {
        Domain domain = domain(model, modules, nonExplicitPolicy);
        JavaPattern services = qualified(serviceTypes, "service type");
        JavaPattern providers = qualified(providerTypes, "provider type");
        ModuleRuleMode value = Objects.requireNonNull(mode, "mode");
        return rule(
                "provides",
                domain,
                value,
                services.description() + ":" + providers.description(),
                module -> module.provides().stream()
                        .map(provide -> provideDeclaration(provide, services, providers, value))
                        .toList());
    }

    private static ArchitectureRule packageDirectives(
            String family,
            TypeModelResult model,
            ModuleSelector modules,
            JavaPattern packages,
            ModulePackageRuleSpec spec,
            NonExplicitModulePolicy nonExplicitPolicy,
            Function<JavaModule, List<JavaModulePackageDirective>> declarations) {
        Domain domain = domain(model, modules, nonExplicitPolicy);
        JavaPattern packagePattern = qualified(packages, "package");
        ModulePackageRuleSpec value = Objects.requireNonNull(spec, "spec");
        return rule(
                family,
                domain,
                value.mode(),
                packagePattern.description() + ":" + value,
                module -> declarations.apply(module).stream()
                        .map(directive -> packageDeclaration(directive, packagePattern, value))
                        .toList());
    }

    private static ArchitectureRule rule(
            String family,
            Domain domain,
            ModuleRuleMode mode,
            String conditionKey,
            Function<JavaModule, List<Declaration>> declarations) {
        String identity = RuleIdentities.semantic(
                "module-descriptor",
                family,
                domain.description.text(),
                mode.name(),
                conditionKey,
                domain.nonExplicitPolicy.name());
        return ArchitectureRules.define(
                identity,
                domain.description + " satisfy " + mode + " " + family + " descriptor policy",
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        domain.description,
                        domain.selected.size(),
                        terminalDiagnostics -> evaluate(
                                metadata, family, domain, mode, declarations, terminalDiagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            String family,
            Domain domain,
            ModuleRuleMode mode,
            Function<JavaModule, List<Declaration>> declarations,
            List<Diagnostic> terminalDiagnostics) {
        List<Diagnostic> diagnostics = new ArrayList<>(terminalDiagnostics);
        List<JavaModule> nonExplicit = domain.selected.stream()
                .filter(module -> module.identity().kind() != JavaModuleKind.EXPLICIT)
                .toList();
        if (!nonExplicit.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    "module-descriptor.non-explicit",
                    domain.nonExplicitPolicy == NonExplicitModulePolicy.REJECT
                            ? Severity.ERROR : Severity.INFO,
                    Map.of(
                            "modules", nonExplicit.stream()
                                    .map(module -> module.identity().stableKey()).toList().toString(),
                            "policy", domain.nonExplicitPolicy.name(),
                            "reason", "NO_MODULE_ATTRIBUTE")));
            if (domain.nonExplicitPolicy == NonExplicitModulePolicy.REJECT) {
                return RuleResult.incomplete(metadata, List.of(), diagnostics);
            }
        }
        List<JavaModule> explicit = domain.selected.stream()
                .filter(module -> module.identity().kind() == JavaModuleKind.EXPLICIT)
                .toList();
        if (explicit.isEmpty() && !nonExplicit.isEmpty()) {
            return RuleResult.skipped(metadata, diagnostics);
        }
        List<Violation> violations = new ArrayList<>();
        for (JavaModule module : explicit) {
            List<Declaration> values = declarations.apply(module);
            switch (mode) {
                case NO -> values.stream().filter(Declaration::matches)
                        .forEach(value -> violations.add(declarationViolation(
                                metadata, family, module, value, "forbidden")));
                case ONLY -> values.stream().filter(Predicate.not(Declaration::matches))
                        .forEach(value -> violations.add(declarationViolation(
                                metadata, family, module, value, "outside-only")));
                case REQUIRED -> {
                    if (values.stream().noneMatch(Declaration::matches)) {
                        violations.add(missingViolation(metadata, family, module));
                    }
                }
            }
        }
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static Violation declarationViolation(
            RuleMetadata metadata,
            String family,
            JavaModule module,
            Declaration declaration,
            String outcome) {
        ModuleIdentityId subject = ModuleIdentityId.of(module.identity());
        TreeMap<String, String> attributes = new TreeMap<>(declaration.attributes);
        attributes.put("declaration", declaration.identity);
        attributes.put("evidenceDomain", "MODULE_DESCRIPTOR");
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(),
                        "module-descriptor." + family + "." + outcome,
                        subject.stableKey(), declaration.identity)),
                "module-descriptor." + family + "." + outcome,
                metadata.severity(),
                List.of(new ViolationSubject("module", subject)),
                declarationEvidence(module),
                attributes);
    }

    private static Violation missingViolation(
            RuleMetadata metadata, String family, JavaModule module) {
        ModuleIdentityId subject = ModuleIdentityId.of(module.identity());
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(),
                        "module-descriptor." + family + ".required",
                        subject.stableKey())),
                "module-descriptor." + family + ".required",
                metadata.severity(),
                List.of(new ViolationSubject("module", subject)),
                declarationEvidence(module),
                Map.of("evidenceDomain", "MODULE_DESCRIPTOR"));
    }

    private static List<DependencyEvidence> declarationEvidence(JavaModule module) {
        return List.of(DependencyEvidence.at(module.location().resource().locationId()));
    }

    private static Declaration packageDeclaration(
            JavaModulePackageDirective directive,
            JavaPattern packages,
            ModulePackageRuleSpec spec) {
        boolean qualificationMatches = switch (spec.qualification()) {
            case ANY -> true;
            case UNQUALIFIED -> !directive.qualified();
            case QUALIFIED -> directive.qualified();
        };
        boolean targetMatches = spec.targetModule()
                .map(pattern -> spec.mode() == ModuleRuleMode.ONLY
                        ? directive.targetModules().stream().allMatch(pattern::matches)
                        : directive.targetModules().stream().anyMatch(pattern::matches))
                .orElse(true);
        return new Declaration(
                directive.packageName().value() + "->" + directive.targetModules(),
                Map.of(
                        "flags", Integer.toUnsignedString(directive.flags()),
                        "package", directive.packageName().value(),
                        "qualified", Boolean.toString(directive.qualified()),
                        "targetModules", directive.targetModules().toString()),
                packages.matches(directive.packageName().value())
                        && qualificationMatches
                        && targetMatches);
    }

    private static Declaration provideDeclaration(
            JavaModuleProvide provide,
            JavaPattern services,
            JavaPattern providers,
            ModuleRuleMode mode) {
        List<String> providerNames = provide.providers().stream()
                .map(JvmReferenceType::binaryName).toList();
        boolean serviceMatches = services.matches(provide.service().binaryName());
        boolean providersMatch = mode == ModuleRuleMode.ONLY
                ? providerNames.stream().allMatch(providers::matches)
                : providerNames.stream().anyMatch(providers::matches);
        return new Declaration(
                provide.service().binaryName() + "->" + providerNames,
                Map.of(
                        "providerTypes", providerNames.toString(),
                        "serviceType", provide.service().binaryName()),
                serviceMatches && providersMatch);
    }

    private static Map<String, String> requiresAttributes(JavaModuleRequire require) {
        return Map.of(
                "compiledVersion", require.compiledVersion().orElse("<unknown>"),
                "flags", Integer.toUnsignedString(require.flags()),
                "staticPhase", Boolean.toString(require.staticPhase()),
                "targetModule", require.moduleName(),
                "transitive", Boolean.toString(require.transitive()));
    }

    private static Domain domain(
            TypeModelResult model,
            ModuleSelector modules,
            NonExplicitModulePolicy nonExplicitPolicy) {
        Objects.requireNonNull(model, "model");
        ModuleSelector selector = Objects.requireNonNull(modules, "modules");
        ModuleSelection selection = selector.selectFrom(model);
        return new Domain(
                selector.description(),
                selection.selected(),
                Objects.requireNonNull(nonExplicitPolicy, "nonExplicitPolicy"));
    }

    private static JavaPattern qualified(JavaPattern pattern, String role) {
        JavaPattern value = Objects.requireNonNull(pattern, role);
        if (value.description().domain() != PatternDomain.QUALIFIED_NAME) {
            throw new IllegalArgumentException(role + " requires QUALIFIED_NAME pattern domain");
        }
        return value;
    }

    private record Declaration(
            String identity, Map<String, String> attributes, boolean matches) {
        private Declaration {
            if (identity == null || identity.isBlank()) {
                throw new IllegalArgumentException("declaration identity must not be blank");
            }
            attributes = Map.copyOf(new TreeMap<>(Objects.requireNonNull(attributes, "attributes")));
        }
    }

    private record Domain(
            dev.archunitjava.selector.SelectorDescription description,
            List<JavaModule> selected,
            NonExplicitModulePolicy nonExplicitPolicy) {
        private Domain {
            Objects.requireNonNull(description, "description");
            selected = selected.stream().distinct().sorted().toList();
            Objects.requireNonNull(nonExplicitPolicy, "nonExplicitPolicy");
        }
    }
}
