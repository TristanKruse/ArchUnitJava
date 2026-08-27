package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.slices.JavaSlice;
import dev.archunitjava.slices.SliceId;
import dev.archunitjava.slices.SliceModel;
import dev.archunitjava.slices.SliceSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Directional and pairwise independence rules over stable, non-overlapping slices. */
public final class SliceRules {
    private SliceRules() {}

    public static ArchitectureRule noDependencies(
            SliceModel slices,
            DependencyGraph graph,
            SliceSelector origins,
            SliceSelector targets) {
        return noDependencies(
                slices, graph, origins, targets, EnumSet.allOf(DependencyKind.class));
    }

    public static ArchitectureRule noDependencies(
            SliceModel slices,
            DependencyGraph graph,
            SliceSelector origins,
            SliceSelector targets,
            Set<DependencyKind> includedKinds) {
        SliceModel model = Objects.requireNonNull(slices, "slices");
        SliceSelector originSelector = Objects.requireNonNull(origins, "origins");
        SliceSelector targetSelector = Objects.requireNonNull(targets, "targets");
        Set<DependencyKind> kinds = kinds(includedKinds);
        List<JavaSlice> selectedOrigins = originSelector.selectFrom(model);
        List<JavaSlice> selectedTargets = targetSelector.selectFrom(model);
        SliceGraphView view = view(model, Objects.requireNonNull(graph, "graph"), kinds);
        String identity = RuleIdentities.semantic(
                "slice-dependency",
                model.definitionKey(),
                originSelector.description().text(),
                targetSelector.description().text(),
                kindKey(kinds));
        return ArchitectureRules.define(
                identity,
                originSelector.description() + " have no dependencies on "
                        + targetSelector.description(),
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        List.of(
                                new RuleSelection(
                                        "origins", originSelector.description(), selectedOrigins.size()),
                                new RuleSelection(
                                        "targets", targetSelector.description(), selectedTargets.size())),
                        diagnostics -> directional(
                                metadata, view, selectedOrigins, selectedTargets, diagnostics)));
    }

    public static ArchitectureRule mutuallyIndependent(
            SliceModel slices, DependencyGraph graph, SliceSelector selected) {
        return mutuallyIndependent(
                slices, graph, selected, EnumSet.allOf(DependencyKind.class));
    }

    public static ArchitectureRule mutuallyIndependent(
            SliceModel slices,
            DependencyGraph graph,
            SliceSelector selected,
            Set<DependencyKind> includedKinds) {
        SliceModel model = Objects.requireNonNull(slices, "slices");
        SliceSelector selector = Objects.requireNonNull(selected, "selected");
        Set<DependencyKind> kinds = kinds(includedKinds);
        List<JavaSlice> selection = selector.selectFrom(model);
        SliceGraphView view = view(model, Objects.requireNonNull(graph, "graph"), kinds);
        String identity = RuleIdentities.semantic(
                "slice-mutual-independence",
                model.definitionKey(),
                selector.description().text(),
                kindKey(kinds));
        return ArchitectureRules.define(
                identity,
                "every distinct pair of " + selector.description() + " is mutually independent",
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        selector.description(),
                        selection.size(),
                        diagnostics -> pairwise(metadata, view, selection, diagnostics)));
    }

    private static RuleResult directional(
            RuleMetadata metadata,
            SliceGraphView view,
            Collection<JavaSlice> origins,
            Collection<JavaSlice> targets,
            List<Diagnostic> diagnostics) {
        Set<SliceId> originIds = ids(origins);
        Set<SliceId> targetIds = ids(targets);
        List<Violation> violations = view.edges.entrySet().stream()
                .filter(entry -> originIds.contains(entry.getKey().origin)
                        && targetIds.contains(entry.getKey().target))
                .map(entry -> directionalViolation(metadata, entry.getKey(), entry.getValue()))
                .sorted()
                .toList();
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static RuleResult pairwise(
            RuleMetadata metadata,
            SliceGraphView view,
            List<JavaSlice> slices,
            List<Diagnostic> diagnostics) {
        List<JavaSlice> selected = slices.stream().distinct().sorted().toList();
        List<Violation> violations = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < selected.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < selected.size(); rightIndex++) {
                SliceId left = selected.get(leftIndex).id();
                SliceId right = selected.get(rightIndex).id();
                EdgeGroup forward = view.edges.get(new DirectedPair(left, right));
                EdgeGroup reverse = view.edges.get(new DirectedPair(right, left));
                if (forward != null || reverse != null) {
                    violations.add(pairViolation(metadata, left, right, forward, reverse));
                }
            }
        }
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static Violation directionalViolation(
            RuleMetadata metadata, DirectedPair pair, EdgeGroup edge) {
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(),
                        "slice.dependency.forbidden",
                        pair.origin.stableKey(),
                        pair.target.stableKey())),
                "slice.dependency.forbidden",
                metadata.severity(),
                List.of(
                        new ViolationSubject("originSlice", pair.origin),
                        new ViolationSubject("targetSlice", pair.target)),
                edge.evidence(),
                attributes(List.of(edge), List.of(pair)));
    }

    private static Violation pairViolation(
            RuleMetadata metadata,
            SliceId left,
            SliceId right,
            EdgeGroup forward,
            EdgeGroup reverse) {
        List<EdgeGroup> groups = java.util.stream.Stream.of(forward, reverse)
                .filter(Objects::nonNull)
                .toList();
        List<DirectedPair> directions = new ArrayList<>();
        if (forward != null) directions.add(new DirectedPair(left, right));
        if (reverse != null) directions.add(new DirectedPair(right, left));
        List<DependencyEvidence> evidence = groups.stream()
                .map(EdgeGroup::evidence)
                .flatMap(Collection::stream)
                .distinct().sorted().toList();
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(),
                        "slice.mutual-independence",
                        left.stableKey(),
                        right.stableKey())),
                "slice.mutual-independence",
                metadata.severity(),
                List.of(
                        new ViolationSubject("leftSlice", left),
                        new ViolationSubject("rightSlice", right)),
                evidence,
                attributes(groups, directions));
    }

    private static Map<String, String> attributes(
            Collection<EdgeGroup> groups, Collection<DirectedPair> directions) {
        List<UnderlyingTypeEdge> typeEdges = groups.stream()
                .map(EdgeGroup::typeEdges)
                .flatMap(Collection::stream)
                .distinct().sorted().toList();
        List<String> members = groups.stream()
                .map(EdgeGroup::evidence)
                .flatMap(Collection::stream)
                .map(DependencyEvidence::ownerMember)
                .flatMap(java.util.Optional::stream)
                .map(StableId::stableKey)
                .distinct().sorted().toList();
        List<String> kinds = typeEdges.stream()
                .map(edge -> edge.kind.name())
                .distinct().sorted().toList();
        List<String> directionKeys = directions.stream()
                .sorted()
                .map(DirectedPair::stableKey)
                .toList();
        return Map.of(
                "dependencyKinds", kinds.toString(),
                "directions", directionKeys.toString(),
                "underlyingMembers", members.toString(),
                "underlyingTypeEdgeCount", Integer.toString(typeEdges.size()),
                "underlyingTypeEdges", typeEdges.stream()
                        .map(UnderlyingTypeEdge::stableKey).toList().toString());
    }

    private static SliceGraphView view(
            SliceModel slices, DependencyGraph source, Set<DependencyKind> includedKinds) {
        DependencyGraph types = ProjectionPlan.types()
                .withoutSelfEdges()
                .includingOnly(includedKinds)
                .apply(source)
                .graph();
        TreeMap<DirectedPair, MutableEdgeGroup> groups = new TreeMap<>();
        for (DependencyEdge edge : types.edges()) {
            if (!(edge.origin() instanceof TypeId origin)
                    || !(edge.target() instanceof TypeId target)) continue;
            SliceId originSlice = slices.sliceOf(origin).orElse(null);
            SliceId targetSlice = slices.sliceOf(target).orElse(null);
            if (originSlice == null || targetSlice == null || originSlice.equals(targetSlice)) continue;
            groups.computeIfAbsent(
                            new DirectedPair(originSlice, targetSlice), ignored -> new MutableEdgeGroup())
                    .add(edge, origin, target);
        }
        TreeMap<DirectedPair, EdgeGroup> immutable = new TreeMap<>();
        groups.forEach((key, value) -> immutable.put(key, value.freeze()));
        return new SliceGraphView(Map.copyOf(immutable));
    }

    private static Set<SliceId> ids(Collection<JavaSlice> slices) {
        return Set.copyOf(new TreeSet<>(slices.stream().map(JavaSlice::id).toList()));
    }

    private static Set<DependencyKind> kinds(Set<DependencyKind> includedKinds) {
        Objects.requireNonNull(includedKinds, "includedKinds");
        return includedKinds.isEmpty()
                ? Set.of()
                : java.util.Collections.unmodifiableSet(EnumSet.copyOf(includedKinds));
    }

    private static String kindKey(Set<DependencyKind> kinds) {
        return kinds.stream().map(Enum::name).sorted().toList().toString();
    }

    private record DirectedPair(SliceId origin, SliceId target)
            implements Comparable<DirectedPair> {
        private DirectedPair {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(target, "target");
            if (origin.equals(target)) throw new IllegalArgumentException("Slice pair must be distinct");
        }

        private String stableKey() {
            return origin.stableKey() + "->" + target.stableKey();
        }

        @Override
        public int compareTo(DirectedPair other) {
            return stableKey().compareTo(other.stableKey());
        }
    }

    private record UnderlyingTypeEdge(TypeId origin, TypeId target, DependencyKind kind)
            implements Comparable<UnderlyingTypeEdge> {
        private UnderlyingTypeEdge {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(kind, "kind");
        }

        private String stableKey() {
            return origin.stableKey() + "->" + target.stableKey() + ":" + kind.name();
        }

        @Override
        public int compareTo(UnderlyingTypeEdge other) {
            return stableKey().compareTo(other.stableKey());
        }
    }

    private record EdgeGroup(
            List<UnderlyingTypeEdge> typeEdges, List<DependencyEvidence> evidence) {
        private EdgeGroup {
            typeEdges = typeEdges.stream().distinct().sorted().toList();
            evidence = evidence.stream().distinct().sorted().toList();
        }
    }

    private static final class MutableEdgeGroup {
        private final TreeSet<UnderlyingTypeEdge> typeEdges = new TreeSet<>();
        private final TreeSet<DependencyEvidence> evidence = new TreeSet<>();

        private void add(DependencyEdge edge, TypeId origin, TypeId target) {
            typeEdges.add(new UnderlyingTypeEdge(origin, target, edge.kind()));
            evidence.addAll(edge.evidence());
        }

        private EdgeGroup freeze() {
            return new EdgeGroup(List.copyOf(typeEdges), List.copyOf(evidence));
        }
    }

    private record SliceGraphView(Map<DirectedPair, EdgeGroup> edges) {
        private SliceGraphView {
            edges = Map.copyOf(new TreeMap<>(Objects.requireNonNull(edges, "edges")));
        }
    }
}
