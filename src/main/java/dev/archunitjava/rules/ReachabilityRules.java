package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.projection.CycleAlgorithms;
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
import dev.archunitjava.selector.TypeVisibility;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Bounded graph reachability rules that never claim whole-program runtime liveness. */
public final class ReachabilityRules {
    public static final String SCOPE_DIAGNOSTIC = "reachability.scope";

    private ReachabilityRules() {}

    public static ArchitectureRule unreachablePublicLibraryTypes(
            TypeModelResult model, DependencyGraph graph, TypeSelector subjects) {
        return unreachableTypes(
                model,
                graph,
                subjects,
                TypeSelector.visibility(TypeVisibility.PUBLIC),
                TypeSelector.none(),
                TypeSelector.none(),
                TypeSelector.none(),
                ReachabilityRuleOptions.publicLibraryDefaults());
    }

    public static ArchitectureRule unreachableTypes(
            TypeModelResult model,
            DependencyGraph graph,
            TypeSelector subjects,
            TypeSelector roots,
            TypeSelector externalConsumers,
            TypeSelector reflectionSensitive,
            TypeSelector ignored,
            ReachabilityRuleOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        TypeSelector subjectSelector = Objects.requireNonNull(subjects, "subjects");
        TypeSelector rootSelector = Objects.requireNonNull(roots, "roots");
        TypeSelector externalSelector = Objects.requireNonNull(externalConsumers, "externalConsumers");
        TypeSelector reflectionSelector = Objects.requireNonNull(
                reflectionSensitive, "reflectionSensitive");
        TypeSelector ignoredSelector = Objects.requireNonNull(ignored, "ignored");
        TypeSelection subjectSelection = subjectSelector.selectFrom(model);
        TypeSelection rootSelection = rootSelector.selectFrom(model);
        TypeSelection externalSelection = externalSelector.selectFrom(model);
        TypeSelection reflectionSelection = reflectionSelector.selectFrom(model);
        TypeSelection ignoredSelection = ignoredSelector.selectFrom(model);
        List<StableId> known = typeIds(model.types());
        Map<StableId, DependencyEvidence> declarations = typeDeclarations(model.types());
        return rule(new Domain(
                "types",
                subjectSelector.description(),
                rootSelector.description(),
                externalSelector.description(),
                reflectionSelector.description(),
                ignoredSelector.description(),
                typeIds(subjectSelection.selected()),
                union(
                        typeIds(rootSelection.selected()),
                        typeIds(externalSelection.selected()),
                        typeIds(reflectionSelection.selected())),
                typeIds(ignoredSelection.selected()),
                known,
                declarations,
                ProjectionPlan.types()
                        .includingOnly(options.includedKinds())
                        .withoutSelfEdges()
                        .apply(graph).graph(),
                options));
    }

    public static ArchitectureRule unreachablePublicLibraryPackages(
            TypeModelResult model, DependencyGraph graph, PackageSelector subjects) {
        return unreachablePackages(
                model,
                graph,
                subjects,
                PackageSelector.containing(TypeSelector.visibility(TypeVisibility.PUBLIC)),
                PackageSelector.none(),
                PackageSelector.none(),
                PackageSelector.none(),
                ReachabilityRuleOptions.publicLibraryDefaults());
    }

    public static ArchitectureRule unreachablePackages(
            TypeModelResult model,
            DependencyGraph graph,
            PackageSelector subjects,
            PackageSelector roots,
            PackageSelector externalConsumers,
            PackageSelector reflectionSensitive,
            PackageSelector ignored,
            ReachabilityRuleOptions options) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        PackageSelector subjectSelector = Objects.requireNonNull(subjects, "subjects");
        PackageSelector rootSelector = Objects.requireNonNull(roots, "roots");
        PackageSelector externalSelector = Objects.requireNonNull(externalConsumers, "externalConsumers");
        PackageSelector reflectionSelector = Objects.requireNonNull(
                reflectionSensitive, "reflectionSensitive");
        PackageSelector ignoredSelector = Objects.requireNonNull(ignored, "ignored");
        PackageSelection subjectSelection = subjectSelector.selectFrom(model);
        PackageSelection rootSelection = rootSelector.selectFrom(model);
        PackageSelection externalSelection = externalSelector.selectFrom(model);
        PackageSelection reflectionSelection = reflectionSelector.selectFrom(model);
        PackageSelection ignoredSelection = ignoredSelector.selectFrom(model);
        List<JavaPackage> packages = model.packages().all();
        return rule(new Domain(
                "packages",
                subjectSelector.description(),
                rootSelector.description(),
                externalSelector.description(),
                reflectionSelector.description(),
                ignoredSelector.description(),
                packageIds(subjectSelection.selected()),
                union(
                        packageIds(rootSelection.selected()),
                        packageIds(externalSelection.selected()),
                        packageIds(reflectionSelection.selected())),
                packageIds(ignoredSelection.selected()),
                packageIds(packages),
                packageDeclarations(packages),
                ProjectionPlan.packages()
                        .includingOnly(options.includedKinds())
                        .withoutSelfEdges()
                        .apply(graph).graph(),
                options));
    }

    private static ArchitectureRule rule(Domain domain) {
        String identity = RuleIdentities.semantic(
                "reachability",
                domain.kind,
                domain.subjectsDescription.text(),
                domain.rootsDescription.text(),
                domain.externalDescription.text(),
                domain.reflectionDescription.text(),
                domain.ignoredDescription.text(),
                optionsKey(domain.options));
        return ArchitectureRules.define(
                identity,
                domain.subjectsDescription + " are reachable from configured " + domain.kind
                        + " entry points",
                (metadata, checkOptions) -> RuleTerminal.evaluate(
                        metadata,
                        checkOptions,
                        List.of(
                                new RuleSelection(
                                        "subjects", domain.subjectsDescription, domain.subjects.size()),
                                new RuleSelection(
                                        "entryPoints",
                                        new SelectorDescription("union of roots, external consumers, and reflection-sensitive subjects"),
                                        effectiveEntries(domain).size())),
                        diagnostics -> evaluate(metadata, domain, diagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata, Domain domain, List<Diagnostic> terminalDiagnostics) {
        DependencyGraph graph = induced(domain.graph, domain.known, domain.ignored);
        Set<StableId> reachable = reachable(graph, effectiveEntries(domain));
        List<StableId> unreachable = domain.subjects.stream()
                .filter(subject -> !domain.ignored.contains(subject))
                .filter(subject -> !reachable.contains(subject))
                .toList();
        DependencyGraph unreachableGraph = induced(graph, unreachable, List.of());
        List<StronglyConnectedComponent> regions = CycleAlgorithms
                .stronglyConnectedComponents(unreachableGraph);
        List<Violation> violations = regions.stream()
                .map(region -> violation(metadata, domain, unreachableGraph, region))
                .sorted()
                .toList();
        List<Diagnostic> diagnostics = new ArrayList<>(terminalDiagnostics);
        diagnostics.add(new Diagnostic(
                SCOPE_DIAGNOSTIC,
                Severity.INFO,
                Map.of(
                        "assumption", domain.options.assumption().name(),
                        "claim", "BOUNDED_GRAPH_REACHABILITY_ONLY",
                        "wholeProgramLiveness", "NOT_CLAIMED")));
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static Violation violation(
            RuleMetadata metadata,
            Domain domain,
            DependencyGraph graph,
            StronglyConnectedComponent region) {
        List<StableId> boundedSubjects = region.nodes().stream()
                .limit(domain.options.maximumSubjectsPerRegion())
                .toList();
        List<DependencyEvidence> allEvidence = graph.edges().stream()
                .filter(edge -> region.nodes().contains(edge.origin())
                        && region.nodes().contains(edge.target()))
                .map(DependencyEdge::evidence)
                .flatMap(Collection::stream)
                .distinct().sorted().toList();
        if (allEvidence.isEmpty()) {
            allEvidence = region.nodes().stream()
                    .map(domain.declarations::get)
                    .filter(Objects::nonNull)
                    .distinct().sorted().toList();
        }
        boolean evidenceTruncated = allEvidence.size() > domain.options.maximumEvidenceEntries();
        List<DependencyEvidence> evidence = allEvidence.stream()
                .limit(domain.options.maximumEvidenceEntries())
                .toList();
        String regionKey = region.nodes().stream().map(StableId::stableKey).toList().toString();
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), "reachability.unreachable-region", regionKey)),
                "reachability.unreachable-region",
                metadata.severity(),
                boundedSubjects.stream()
                        .map(subject -> new ViolationSubject("unreachableSubject", subject))
                        .toList(),
                evidence,
                Map.of(
                        "assumption", domain.options.assumption().name(),
                        "cyclicRegion", Boolean.toString(region.cyclic()),
                        "domain", domain.kind,
                        "evidenceTruncated", Boolean.toString(evidenceTruncated),
                        "regionSize", Integer.toString(region.nodes().size()),
                        "subjectsTruncated", Boolean.toString(
                                region.nodes().size() > boundedSubjects.size()),
                        "wholeProgramLiveness", "NOT_CLAIMED"));
    }

    private static Set<StableId> reachable(
            DependencyGraph graph, Collection<? extends StableId> entries) {
        TreeMap<StableId, List<StableId>> outgoing = new TreeMap<>();
        graph.nodes().forEach(node -> outgoing.put(node.id(), new ArrayList<>()));
        graph.edges().forEach(edge -> outgoing.get(edge.origin()).add(edge.target()));
        outgoing.replaceAll((node, targets) -> targets.stream().distinct().sorted().toList());
        TreeSet<StableId> reached = new TreeSet<>();
        Deque<StableId> queue = new ArrayDeque<>();
        entries.stream().distinct().sorted()
                .filter(outgoing::containsKey)
                .forEach(entry -> {
                    if (reached.add(entry)) queue.addLast(entry);
                });
        while (!queue.isEmpty()) {
            StableId current = queue.removeFirst();
            for (StableId target : outgoing.get(current)) {
                if (reached.add(target)) queue.addLast(target);
            }
        }
        return Set.copyOf(reached);
    }

    private static DependencyGraph induced(
            DependencyGraph source,
            Collection<? extends StableId> included,
            Collection<? extends StableId> excluded) {
        Set<StableId> ids = new HashSet<>(included);
        ids.removeAll(new HashSet<>(excluded));
        DependencyGraph.Builder result = DependencyGraph.builder();
        source.nodes().stream().map(node -> node.id()).filter(ids::contains)
                .forEach(result::addNode);
        source.edges().stream()
                .filter(edge -> ids.contains(edge.origin()) && ids.contains(edge.target()))
                .forEach(edge -> edge.evidence().forEach(evidence -> result.addDependency(
                        edge.origin(), edge.target(), edge.kind(), evidence)));
        return result.build();
    }

    private static List<StableId> effectiveEntries(Domain domain) {
        return domain.entries.stream()
                .filter(entry -> !domain.ignored.contains(entry))
                .distinct().sorted().toList();
    }

    @SafeVarargs
    private static List<StableId> union(List<StableId>... values) {
        return java.util.Arrays.stream(values)
                .flatMap(Collection::stream)
                .distinct().sorted().toList();
    }

    private static List<StableId> typeIds(Collection<JavaType> types) {
        return types.stream()
                .map(type -> (StableId) TypeId.ofBinaryName(type.binaryName()))
                .distinct().sorted().toList();
    }

    private static List<StableId> packageIds(Collection<JavaPackage> packages) {
        return packages.stream().map(value -> (StableId) packageId(value))
                .distinct().sorted().toList();
    }

    private static PackageId packageId(JavaPackage value) {
        return value.name().isUnnamed()
                ? PackageId.unnamed()
                : PackageId.named(value.name().value());
    }

    private static Map<StableId, DependencyEvidence> typeDeclarations(
            Collection<JavaType> types) {
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
            List<JavaType> types = java.util.stream.Stream.concat(
                            value.types().stream(), value.packageInfoTypes().stream())
                    .sorted().toList();
            if (!types.isEmpty()) {
                result.put(packageId(value), DependencyEvidence.at(
                        types.getFirst().location().resource().locationId()));
            }
        }
        return Map.copyOf(result);
    }

    private static String optionsKey(ReachabilityRuleOptions options) {
        return options.assumption()
                + ":kinds=" + options.includedKinds().stream().map(Enum::name).sorted().toList()
                + ":subjects=" + options.maximumSubjectsPerRegion()
                + ":evidence=" + options.maximumEvidenceEntries();
    }

    private record Domain(
            String kind,
            SelectorDescription subjectsDescription,
            SelectorDescription rootsDescription,
            SelectorDescription externalDescription,
            SelectorDescription reflectionDescription,
            SelectorDescription ignoredDescription,
            List<StableId> subjects,
            List<StableId> entries,
            List<StableId> ignored,
            List<StableId> known,
            Map<StableId, DependencyEvidence> declarations,
            DependencyGraph graph,
            ReachabilityRuleOptions options) {
        private Domain {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(subjectsDescription, "subjectsDescription");
            Objects.requireNonNull(rootsDescription, "rootsDescription");
            Objects.requireNonNull(externalDescription, "externalDescription");
            Objects.requireNonNull(reflectionDescription, "reflectionDescription");
            Objects.requireNonNull(ignoredDescription, "ignoredDescription");
            subjects = subjects.stream().distinct().sorted().toList();
            entries = entries.stream().distinct().sorted().toList();
            ignored = ignored.stream().distinct().sorted().toList();
            known = known.stream().distinct().sorted().toList();
            declarations = Map.copyOf(declarations);
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(options, "options");
        }
    }
}
