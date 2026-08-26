package dev.archunitjava.rules;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.PackageSelection;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.SelectorDescription;
import dev.archunitjava.selector.TypeSelection;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Positive and negative dependency rules over deterministic type or package projections. */
public final class DependencyRules {
    private static final String EXTERNAL_DIAGNOSTIC = "rule.dependency.external-target";

    private DependencyRules() {}

    public static ArchitectureRule types(
            TypeModelResult model,
            DependencyGraph graph,
            TypeSelector origins,
            TypeSelector targets,
            DependencyRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        TypeSelector originSelector = Objects.requireNonNull(origins, "origins");
        TypeSelector targetSelector = Objects.requireNonNull(targets, "targets");
        DependencyRuleSpec value = Objects.requireNonNull(spec, "spec");
        TypeSelection selectedOrigins = originSelector.selectFrom(model);
        TypeSelection selectedTargets = targetSelector.selectFrom(model);
        List<JavaType> all = model.types();
        Domain domain = new Domain(
                "types",
                originSelector.description(),
                targetSelector.description(),
                ids(selectedOrigins.selected()),
                ids(selectedTargets.selected()),
                ids(all),
                typeDeclarations(all),
                ProjectionPlan.types().apply(graph).graph());
        return rule(domain, value);
    }

    public static ArchitectureRule packages(
            TypeModelResult model,
            DependencyGraph graph,
            PackageSelector origins,
            PackageSelector targets,
            DependencyRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        PackageSelector originSelector = Objects.requireNonNull(origins, "origins");
        PackageSelector targetSelector = Objects.requireNonNull(targets, "targets");
        DependencyRuleSpec value = Objects.requireNonNull(spec, "spec");
        PackageSelection selectedOrigins = originSelector.selectFrom(model);
        PackageSelection selectedTargets = targetSelector.selectFrom(model);
        List<JavaPackage> all = model.packages().all();
        Domain domain = new Domain(
                "packages",
                originSelector.description(),
                targetSelector.description(),
                packageIds(selectedOrigins.selected()),
                packageIds(selectedTargets.selected()),
                packageIds(all),
                packageDeclarations(all),
                ProjectionPlan.packages().apply(graph).graph());
        return rule(domain, value);
    }

    private static ArchitectureRule rule(Domain domain, DependencyRuleSpec spec) {
        String identity = RuleIdentities.semantic(
                "dependency",
                domain.kind,
                domain.originsDescription.text(),
                domain.targetsDescription.text(),
                spec.toString());
        String description = switch (spec.mode()) {
            case NO -> domain.originsDescription + " have no dependencies on " + domain.targetsDescription;
            case ONLY -> domain.originsDescription + " only depend on " + domain.targetsDescription;
            case ANY -> domain.originsDescription + " have any dependency on " + domain.targetsDescription;
            case REQUIRED -> domain.originsDescription + " each require a dependency on "
                    + domain.targetsDescription;
        };
        return ArchitectureRules.define(identity, description,
                (metadata, options) -> evaluate(metadata, options, domain, spec));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            CheckOptions options,
            Domain domain,
            DependencyRuleSpec spec) {
        return RuleTerminal.evaluate(
                metadata,
                options,
                List.of(
                        new RuleSelection(
                                "origins", domain.originsDescription, domain.origins.size()),
                        new RuleSelection(
                                "targets", domain.targetsDescription, domain.targets.size())),
                terminalDiagnostics -> ordinary(
                        metadata, options, domain, spec, terminalDiagnostics));
    }

    private static RuleResult ordinary(
            RuleMetadata metadata,
            CheckOptions options,
            Domain domain,
            DependencyRuleSpec spec,
            List<Diagnostic> terminalDiagnostics) {
        TreeMap<StableId, List<DependencyEdge>> outgoing = new TreeMap<>();
        domain.origins.forEach(origin -> outgoing.put(origin, new ArrayList<>()));
        List<Diagnostic> diagnostics = new ArrayList<>(terminalDiagnostics);
        boolean externalFailure = false;
        for (DependencyEdge edge : domain.graph.edges()) {
            if (!domain.origins.contains(edge.origin())) continue;
            if (spec.selfDependencies() == SelfDependencyPolicy.IGNORE
                    && edge.origin().equals(edge.target())) continue;
            boolean external = !domain.known.contains(edge.target());
            if (external && spec.externalDependencies() == ExternalDependencyPolicy.IGNORE) continue;
            if (external && spec.externalDependencies() == ExternalDependencyPolicy.FAIL) {
                externalFailure = true;
                diagnostics.add(new Diagnostic(
                        EXTERNAL_DIAGNOSTIC,
                        Severity.ERROR,
                        Map.of(
                                "origin", edge.origin().stableKey(),
                                "remediation", "Import the target or choose an explicit non-failing external policy",
                                "target", edge.target().stableKey())));
                outgoing.get(edge.origin()).add(edge);
                continue;
            }
            outgoing.get(edge.origin()).add(edge);
        }

        List<Violation> violations = switch (spec.mode()) {
            case NO -> edgeViolations(metadata, domain, outgoing, true);
            case ONLY -> edgeViolations(metadata, domain, outgoing, false);
            case ANY -> anyViolation(metadata, domain, outgoing);
            case REQUIRED -> requiredViolations(metadata, domain, outgoing);
        };
        if (externalFailure && !options.allowIncompleteAnalysis()) {
            return RuleResult.incomplete(metadata, violations, diagnostics);
        }
        if (violations.isEmpty()) return RuleResult.passed(metadata, diagnostics);
        return RuleResult.failed(metadata, violations, diagnostics);
    }

    private static List<Violation> edgeViolations(
            RuleMetadata metadata,
            Domain domain,
            Map<StableId, List<DependencyEdge>> outgoing,
            boolean matchingEdgesViolate) {
        List<Violation> violations = new ArrayList<>();
        outgoing.values().stream().flatMap(Collection::stream).forEach(edge -> {
            boolean matches = domain.targets.contains(edge.target());
            if (matches == matchingEdgesViolate) {
                violations.add(edgeViolation(metadata, domain, edge,
                        matchingEdgesViolate ? "dependency.forbidden" : "dependency.outside-only"));
            }
        });
        return violations.stream().sorted().toList();
    }

    private static List<Violation> anyViolation(
            RuleMetadata metadata,
            Domain domain,
            Map<StableId, List<DependencyEdge>> outgoing) {
        boolean any = outgoing.values().stream().flatMap(Collection::stream)
                .anyMatch(edge -> domain.targets.contains(edge.target()));
        if (any) return List.of();
        StableId representative = domain.origins.getFirst();
        return List.of(missingViolation(
                metadata, domain, representative, "dependency.any-required"));
    }

    private static List<Violation> requiredViolations(
            RuleMetadata metadata,
            Domain domain,
            Map<StableId, List<DependencyEdge>> outgoing) {
        return domain.origins.stream()
                .filter(origin -> outgoing.get(origin).stream()
                        .noneMatch(edge -> domain.targets.contains(edge.target())))
                .map(origin -> missingViolation(
                        metadata, domain, origin, "dependency.required"))
                .sorted()
                .toList();
    }

    private static Violation edgeViolation(
            RuleMetadata metadata, Domain domain, DependencyEdge edge, String code) {
        List<DependencyEvidence> evidence = edge.evidence().isEmpty()
                ? declarationEvidence(domain, edge.origin()) : edge.evidence();
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code,
                        edge.origin().stableKey(), edge.target().stableKey(), edge.kind().name())),
                code,
                metadata.severity(),
                List.of(
                        new ViolationSubject("origin", edge.origin()),
                        new ViolationSubject("target", edge.target())),
                evidence,
                Map.of("dependencyKind", edge.kind().name(), "domain", domain.kind));
    }

    private static Violation missingViolation(
            RuleMetadata metadata, Domain domain, StableId origin, String code) {
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code, origin.stableKey())),
                code,
                metadata.severity(),
                List.of(new ViolationSubject("origin", origin)),
                declarationEvidence(domain, origin),
                Map.of(
                        "domain", domain.kind,
                        "requiredTargetSelector", domain.targetsDescription.text()));
    }

    private static List<DependencyEvidence> declarationEvidence(Domain domain, StableId origin) {
        DependencyEvidence evidence = domain.declarations.get(origin);
        if (evidence == null) {
            throw new IllegalStateException("Selected origin lacks declaration evidence: " + origin.stableKey());
        }
        return List.of(evidence);
    }

    private static List<StableId> ids(Collection<JavaType> types) {
        return types.stream().map(type -> (StableId) TypeId.ofBinaryName(type.binaryName()))
                .distinct().sorted().toList();
    }

    private static List<StableId> packageIds(Collection<JavaPackage> packages) {
        return packages.stream().map(value -> (StableId) packageId(value))
                .distinct().sorted().toList();
    }

    private static PackageId packageId(JavaPackage value) {
        return value.name().isUnnamed()
                ? PackageId.unnamed() : PackageId.named(value.name().value());
    }

    private static Map<StableId, DependencyEvidence> typeDeclarations(Collection<JavaType> types) {
        TreeMap<StableId, DependencyEvidence> result = new TreeMap<>();
        types.forEach(type -> result.put(
                TypeId.ofBinaryName(type.binaryName()),
                DependencyEvidence.at(type.location().resource().locationId())));
        return Map.copyOf(result);
    }

    private static Map<StableId, DependencyEvidence> packageDeclarations(
            Collection<JavaPackage> packages) {
        TreeMap<StableId, DependencyEvidence> result = new TreeMap<>();
        for (JavaPackage value : packages) {
            List<JavaType> representatives = java.util.stream.Stream.concat(
                            value.types().stream(), value.packageInfoTypes().stream())
                    .sorted().toList();
            if (!representatives.isEmpty()) {
                result.put(packageId(value), DependencyEvidence.at(
                        representatives.getFirst().location().resource().locationId()));
            }
        }
        return Map.copyOf(result);
    }

    private record Domain(
            String kind,
            SelectorDescription originsDescription,
            SelectorDescription targetsDescription,
            List<StableId> origins,
            List<StableId> targets,
            List<StableId> known,
            Map<StableId, DependencyEvidence> declarations,
            DependencyGraph graph) {
        private Domain {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(originsDescription, "originsDescription");
            Objects.requireNonNull(targetsDescription, "targetsDescription");
            origins = origins.stream().distinct().sorted().toList();
            targets = targets.stream().distinct().sorted().toList();
            known = known.stream().distinct().sorted().toList();
            declarations = Map.copyOf(Objects.requireNonNull(declarations, "declarations"));
            Objects.requireNonNull(graph, "graph");
        }
    }
}
