package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaModifier;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.projection.CycleAlgorithms;
import dev.archunitjava.projection.CycleEnumerationOptions;
import dev.archunitjava.projection.ElementaryCycle;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.projection.StronglyConnectedComponent;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Cycle-free type and package rules with induced selections and bounded representative evidence. */
public final class CycleRules {
    private static final Set<DependencyKind> TYPE_USE_KINDS = Set.copyOf(EnumSet.of(
            DependencyKind.ANNOTATION,
            DependencyKind.CLASS_LITERAL,
            DependencyKind.EXTENDS,
            DependencyKind.FIELD_TYPE,
            DependencyKind.GENERIC_SIGNATURE,
            DependencyKind.IMPLEMENTS,
            DependencyKind.INSTANCEOF,
            DependencyKind.METHOD_PARAMETER_TYPE,
            DependencyKind.METHOD_RETURN_TYPE,
            DependencyKind.THROWS,
            DependencyKind.TYPE_REFERENCE));

    private CycleRules() {}

    public static ArchitectureRule typesAreAcyclic(
            TypeModelResult model, DependencyGraph graph, TypeSelector types) {
        return typesAreAcyclic(model, graph, types, CycleRuleOptions.defaults());
    }

    public static ArchitectureRule typesAreAcyclic(
            TypeModelResult model,
            DependencyGraph graph,
            TypeSelector types,
            CycleRuleOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        TypeSelector selector = Objects.requireNonNull(types, "types");
        CycleRuleOptions value = Objects.requireNonNull(options, "options");
        TypeSelection selection = selector.selectFrom(model);
        List<StableId> selected = selection.selected().stream()
                .map(type -> (StableId) TypeId.ofBinaryName(type.binaryName()))
                .distinct().sorted().toList();
        return rule(new Domain(
                "types",
                selector.description(),
                selected,
                prepare(model, graph, ProjectionPlan.types().withoutSelfEdges(), selected, value),
                value));
    }

    public static ArchitectureRule packagesAreAcyclic(
            TypeModelResult model, DependencyGraph graph, PackageSelector packages) {
        return packagesAreAcyclic(model, graph, packages, CycleRuleOptions.defaults());
    }

    public static ArchitectureRule packagesAreAcyclic(
            TypeModelResult model,
            DependencyGraph graph,
            PackageSelector packages,
            CycleRuleOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        PackageSelector selector = Objects.requireNonNull(packages, "packages");
        CycleRuleOptions value = Objects.requireNonNull(options, "options");
        PackageSelection selection = selector.selectFrom(model);
        List<StableId> selected = selection.selected().stream()
                .map(valuePackage -> (StableId) packageId(valuePackage))
                .distinct().sorted().toList();
        return rule(new Domain(
                "packages",
                selector.description(),
                selected,
                prepare(model, graph, ProjectionPlan.packages().withoutSelfEdges(), selected, value),
                value));
    }

    private static ArchitectureRule rule(Domain domain) {
        String identity = RuleIdentities.semantic(
                "cycle",
                domain.kind,
                domain.selector.text(),
                semanticKey(domain.options));
        return ArchitectureRules.define(
                identity,
                domain.selector + " are free of " + domain.kind + " dependency cycles",
                (metadata, checkOptions) -> RuleTerminal.evaluate(
                        metadata,
                        checkOptions,
                        domain.selector,
                        domain.selected.size(),
                        diagnostics -> evaluate(metadata, domain, diagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata, Domain domain, List<Diagnostic> terminalDiagnostics) {
        List<StronglyConnectedComponent> components = CycleAlgorithms
                .stronglyConnectedComponents(domain.graph).stream()
                .filter(StronglyConnectedComponent::cyclic)
                .toList();
        List<Diagnostic> diagnostics = new ArrayList<>(terminalDiagnostics);
        List<Violation> violations = new ArrayList<>();
        int truncatedRepresentatives = 0;
        for (StronglyConnectedComponent component : components) {
            Representative representative = representative(domain.graph, component, domain.options);
            if (representative.truncated) truncatedRepresentatives++;
            violations.add(violation(metadata, domain, component, representative));
        }
        if (truncatedRepresentatives > 0) {
            diagnostics.add(new Diagnostic(
                    "cycle.representative.truncated",
                    Severity.WARNING,
                    Map.of(
                            "components", Integer.toString(truncatedRepresentatives),
                            "maximumLength", Integer.toString(
                                    domain.options.maximumRepresentativeLength()),
                            "maximumTraversedEdgesPerComponent", Long.toString(
                                    domain.options.maximumTraversedEdgesPerComponent()))));
        }
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static Violation violation(
            RuleMetadata metadata,
            Domain domain,
            StronglyConnectedComponent component,
            Representative representative) {
        List<StableId> subjects = representative.cycle
                .map(ElementaryCycle::nodes)
                .orElseGet(() -> List.of(component.nodes().getFirst()));
        List<ViolationSubject> violationSubjects = subjects.stream()
                .map(value -> new ViolationSubject("cycleNode", value))
                .toList();
        List<DependencyEvidence> allEvidence = representative.evidence.stream()
                .distinct().sorted().toList();
        boolean evidenceTruncated = allEvidence.size() > domain.options.maximumEvidenceEntries();
        List<DependencyEvidence> evidence = allEvidence.stream()
                .limit(domain.options.maximumEvidenceEntries())
                .toList();
        String representativePath = representative.cycle
                .map(cycle -> cycle.closedPath().stream().map(StableId::stableKey).toList().toString())
                .orElse("<unavailable-within-bounds>");
        String componentKey = component.nodes().stream()
                .map(StableId::stableKey).toList().toString();
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), "cycle.detected", componentKey)),
                "cycle.detected",
                metadata.severity(),
                violationSubjects,
                evidence,
                Map.of(
                        "componentSize", Integer.toString(component.nodes().size()),
                        "domain", domain.kind,
                        "evidenceTruncated", Boolean.toString(evidenceTruncated),
                        "representativeCycle", representativePath,
                        "representativeSearchTruncated", Boolean.toString(representative.truncated),
                        "syntheticEdges", domain.options.syntheticEdges().name(),
                        "typeUseDependencies", domain.options.typeUseDependencies().name()));
    }

    private static Representative representative(
            DependencyGraph graph,
            StronglyConnectedComponent component,
            CycleRuleOptions options) {
        DependencyGraph componentGraph = induced(graph, component.nodes());
        var analysis = CycleAlgorithms.analyze(
                componentGraph,
                CycleEnumerationOptions.bounded(
                        1,
                        options.maximumRepresentativeLength(),
                        options.maximumTraversedEdgesPerComponent()));
        Optional<ElementaryCycle> cycle = analysis.cycles().stream().findFirst();
        List<DependencyEvidence> evidence = cycle
                .map(value -> cycleEvidence(componentGraph, value))
                .orElse(List.of());
        return new Representative(cycle, evidence, analysis.enumerationTruncated() || cycle.isEmpty());
    }

    private static List<DependencyEvidence> cycleEvidence(
            DependencyGraph graph, ElementaryCycle cycle) {
        List<DependencyEvidence> result = new ArrayList<>();
        List<StableId> path = cycle.closedPath();
        for (int index = 0; index < path.size() - 1; index++) {
            StableId origin = path.get(index);
            StableId target = path.get(index + 1);
            graph.edges().stream()
                    .filter(edge -> edge.origin().equals(origin) && edge.target().equals(target))
                    .forEach(edge -> result.addAll(edge.evidence()));
        }
        return result.stream().distinct().sorted().toList();
    }

    private static DependencyGraph prepare(
            TypeModelResult model,
            DependencyGraph source,
            ProjectionPlan projection,
            List<StableId> selected,
            CycleRuleOptions options) {
        Set<DependencyKind> kinds = effectiveKinds(options);
        DependencyGraph semanticSource = options.syntheticEdges() == SyntheticEdgePolicy.INCLUDE
                ? source
                : withoutSyntheticEvidence(model, source);
        DependencyGraph projected = projection.includingOnly(kinds).apply(semanticSource).graph();
        return induced(projected, selected);
    }

    private static Set<DependencyKind> effectiveKinds(CycleRuleOptions options) {
        EnumSet<DependencyKind> result = options.includedKinds().isEmpty()
                ? EnumSet.noneOf(DependencyKind.class)
                : EnumSet.copyOf(options.includedKinds());
        if (options.typeUseDependencies() == TypeUseDependencyPolicy.IGNORE) {
            result.removeAll(TYPE_USE_KINDS);
        }
        return result;
    }

    private static DependencyGraph induced(
            DependencyGraph source, Collection<? extends StableId> selected) {
        Set<StableId> ids = Set.copyOf(new TreeSet<>(selected));
        DependencyGraph.Builder result = DependencyGraph.builder();
        source.nodes().stream()
                .map(node -> node.id())
                .filter(ids::contains)
                .forEach(result::addNode);
        source.edges().stream()
                .filter(edge -> ids.contains(edge.origin()) && ids.contains(edge.target()))
                .forEach(edge -> edge.evidence().forEach(evidence -> result.addDependency(
                        edge.origin(), edge.target(), edge.kind(), evidence)));
        return result.build();
    }

    private static DependencyGraph withoutSyntheticEvidence(
            TypeModelResult model, DependencyGraph source) {
        Set<MemberId> syntheticMembers = new HashSet<>();
        Set<TypeId> syntheticTypes = new HashSet<>();
        for (JavaType type : model.types()) {
            TypeId typeId = TypeId.ofBinaryName(type.binaryName());
            if (type.modifiers().contains(JavaModifier.SYNTHETIC)) syntheticTypes.add(typeId);
            for (JavaMember member : type.declaredMembers()) {
                if (member.modifiers().contains(JavaMemberModifier.SYNTHETIC)
                        || member.modifiers().contains(JavaMemberModifier.BRIDGE)) {
                    syntheticMembers.add(MemberId.of(
                            typeId, member.name(), member.descriptor()));
                }
            }
        }
        DependencyGraph.Builder result = DependencyGraph.builder();
        source.nodes().forEach(node -> result.addNode(node.id()));
        for (DependencyEdge edge : source.edges()) {
            boolean syntheticOrigin = (edge.origin() instanceof TypeId type
                            && syntheticTypes.contains(type))
                    || (edge.origin() instanceof MemberId member
                            && syntheticMembers.contains(member));
            edge.evidence().stream()
                    .filter(evidence -> !syntheticOrigin
                            && evidence.ownerMember().map(syntheticMembers::contains).orElse(true))
                    .forEach(evidence -> result.addDependency(
                            edge.origin(), edge.target(), edge.kind(), evidence));
        }
        return result.build();
    }

    private static PackageId packageId(JavaPackage value) {
        return value.name().isUnnamed()
                ? PackageId.unnamed()
                : PackageId.named(value.name().value());
    }

    private static String semanticKey(CycleRuleOptions options) {
        return options.includedKinds().stream().map(Enum::name).sorted().toList()
                + ":typeUses=" + options.typeUseDependencies()
                + ":synthetic=" + options.syntheticEdges()
                + ":length=" + options.maximumRepresentativeLength()
                + ":traversed=" + options.maximumTraversedEdgesPerComponent()
                + ":evidence=" + options.maximumEvidenceEntries();
    }

    private record Representative(
            Optional<ElementaryCycle> cycle,
            List<DependencyEvidence> evidence,
            boolean truncated) {
        private Representative {
            Objects.requireNonNull(cycle, "cycle");
            evidence = evidence.stream().distinct().sorted().toList();
        }
    }

    private record Domain(
            String kind,
            SelectorDescription selector,
            List<StableId> selected,
            DependencyGraph graph,
            CycleRuleOptions options) {
        private Domain {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(selector, "selector");
            selected = selected.stream().distinct().sorted().toList();
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(options, "options");
        }
    }
}
