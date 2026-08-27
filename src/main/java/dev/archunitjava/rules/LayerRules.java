package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.JavaLayer;
import dev.archunitjava.layers.LayerId;
import dev.archunitjava.layers.LayerModel;
import dev.archunitjava.layers.LayerSelector;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Named-layer access policies sharing one deterministic type/member evidence projection. */
public final class LayerRules {
    private LayerRules() {}

    public static ArchitectureRule noAccess(
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector origins,
            LayerSelector targets) {
        return noAccess(layers, graph, origins, targets, EnumSet.allOf(DependencyKind.class));
    }

    public static ArchitectureRule noAccess(
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector origins,
            LayerSelector targets,
            Set<DependencyKind> includedKinds) {
        return binary(
                Mode.NO_ACCESS, layers, graph, origins, targets, includedKinds,
                origins.description() + " have no access to " + targets.description());
    }

    public static ArchitectureRule mayOnlyAccess(
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector origins,
            LayerSelector allowedTargets) {
        return mayOnlyAccess(
                layers, graph, origins, allowedTargets, EnumSet.allOf(DependencyKind.class));
    }

    public static ArchitectureRule mayOnlyAccess(
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector origins,
            LayerSelector allowedTargets,
            Set<DependencyKind> includedKinds) {
        return binary(
                Mode.MAY_ONLY_ACCESS,
                layers,
                graph,
                origins,
                allowedTargets,
                includedKinds,
                origins.description() + " may only access " + allowedTargets.description());
    }

    public static ArchitectureRule onlyAccessedBy(
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector targets,
            LayerSelector allowedOrigins) {
        return onlyAccessedBy(
                layers, graph, targets, allowedOrigins, EnumSet.allOf(DependencyKind.class));
    }

    public static ArchitectureRule onlyAccessedBy(
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector targets,
            LayerSelector allowedOrigins,
            Set<DependencyKind> includedKinds) {
        return binary(
                Mode.ONLY_ACCESSED_BY,
                layers,
                graph,
                targets,
                allowedOrigins,
                includedKinds,
                targets.description() + " are only accessed by " + allowedOrigins.description());
    }

    public static ArchitectureRule isolated(
            LayerModel layers, DependencyGraph graph, LayerSelector isolated) {
        return isolated(layers, graph, isolated, EnumSet.allOf(DependencyKind.class));
    }

    public static ArchitectureRule isolated(
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector isolated,
            Set<DependencyKind> includedKinds) {
        LayerModel model = Objects.requireNonNull(layers, "layers");
        LayerSelector selector = Objects.requireNonNull(isolated, "isolated");
        List<JavaLayer> selection = selector.selectFrom(model);
        Set<DependencyKind> kinds = kinds(includedKinds);
        LayerGraphView view = view(model, Objects.requireNonNull(graph, "graph"), kinds);
        String identity = RuleIdentities.semantic(
                "layer-access",
                Mode.ISOLATED.name(),
                model.definitionKey(),
                selector.description().text(),
                kindKey(kinds));
        return ArchitectureRules.define(
                identity,
                selector.description() + " are isolated",
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        selector.description(),
                        selection.size(),
                        diagnostics -> evaluate(
                                metadata,
                                Mode.ISOLATED,
                                view,
                                ids(selection),
                                Set.of(),
                                diagnostics)));
    }

    private static ArchitectureRule binary(
            Mode mode,
            LayerModel layers,
            DependencyGraph graph,
            LayerSelector primary,
            LayerSelector secondary,
            Set<DependencyKind> includedKinds,
            String description) {
        LayerModel model = Objects.requireNonNull(layers, "layers");
        LayerSelector firstSelector = Objects.requireNonNull(primary, "primary");
        LayerSelector secondSelector = Objects.requireNonNull(secondary, "secondary");
        List<JavaLayer> first = firstSelector.selectFrom(model);
        List<JavaLayer> second = secondSelector.selectFrom(model);
        Set<DependencyKind> kinds = kinds(includedKinds);
        LayerGraphView view = view(model, Objects.requireNonNull(graph, "graph"), kinds);
        String identity = RuleIdentities.semantic(
                "layer-access",
                mode.name(),
                model.definitionKey(),
                firstSelector.description().text(),
                secondSelector.description().text(),
                kindKey(kinds));
        List<RuleSelection> selections = new ArrayList<>();
        selections.add(new RuleSelection("subjects", firstSelector.description(), first.size()));
        if (!secondSelector.intentionalEmpty()) {
            selections.add(new RuleSelection("relatedLayers", secondSelector.description(), second.size()));
        }
        return ArchitectureRules.define(
                identity,
                description,
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        selections,
                        diagnostics -> evaluate(
                                metadata, mode, view, ids(first), ids(second), diagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            Mode mode,
            LayerGraphView view,
            Set<LayerId> primary,
            Set<LayerId> secondary,
            List<Diagnostic> diagnostics) {
        List<Violation> violations = view.edges.entrySet().stream()
                .filter(entry -> violates(mode, entry.getKey(), primary, secondary))
                .map(entry -> violation(metadata, mode, entry.getKey(), entry.getValue()))
                .sorted()
                .toList();
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }

    private static boolean violates(
            Mode mode, DirectedPair pair, Set<LayerId> primary, Set<LayerId> secondary) {
        return switch (mode) {
            case NO_ACCESS -> primary.contains(pair.origin) && secondary.contains(pair.target);
            case MAY_ONLY_ACCESS -> primary.contains(pair.origin) && !secondary.contains(pair.target);
            case ONLY_ACCESSED_BY -> primary.contains(pair.target) && !secondary.contains(pair.origin);
            case ISOLATED -> primary.contains(pair.origin) || primary.contains(pair.target);
        };
    }

    private static Violation violation(
            RuleMetadata metadata, Mode mode, DirectedPair pair, EdgeGroup edge) {
        List<String> members = edge.evidence.stream()
                .map(DependencyEvidence::ownerMember)
                .flatMap(java.util.Optional::stream)
                .map(StableId::stableKey)
                .distinct().sorted().toList();
        List<String> typeEdges = edge.typeEdges.stream()
                .map(UnderlyingTypeEdge::stableKey)
                .toList();
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(),
                        "layer.access." + mode.name().toLowerCase(java.util.Locale.ROOT),
                        pair.origin.stableKey(),
                        pair.target.stableKey())),
                "layer.access." + mode.name().toLowerCase(java.util.Locale.ROOT),
                metadata.severity(),
                List.of(
                        new ViolationSubject("originLayer", pair.origin),
                        new ViolationSubject("targetLayer", pair.target)),
                edge.evidence,
                Map.of(
                        "dependencyKinds", edge.typeEdges.stream()
                                .map(value -> value.kind.name()).distinct().sorted().toList().toString(),
                        "underlyingMembers", members.toString(),
                        "underlyingTypeEdges", typeEdges.toString()));
    }

    private static LayerGraphView view(
            LayerModel layers, DependencyGraph source, Set<DependencyKind> includedKinds) {
        DependencyGraph types = ProjectionPlan.types()
                .withoutSelfEdges()
                .includingOnly(includedKinds)
                .apply(source)
                .graph();
        TreeMap<DirectedPair, MutableEdgeGroup> groups = new TreeMap<>();
        for (DependencyEdge edge : types.edges()) {
            if (!(edge.origin() instanceof TypeId origin)
                    || !(edge.target() instanceof TypeId target)) continue;
            LayerId originLayer = layers.layerOf(origin).orElseGet(LayerId::unassigned);
            LayerId targetLayer = layers.layerOf(target).orElseGet(LayerId::unassigned);
            if (originLayer.equals(targetLayer)) continue;
            groups.computeIfAbsent(
                            new DirectedPair(originLayer, targetLayer), ignored -> new MutableEdgeGroup())
                    .add(edge, origin, target);
        }
        TreeMap<DirectedPair, EdgeGroup> immutable = new TreeMap<>();
        groups.forEach((pair, group) -> immutable.put(pair, group.freeze()));
        return new LayerGraphView(Map.copyOf(immutable));
    }

    private static Set<LayerId> ids(Collection<JavaLayer> layers) {
        return Set.copyOf(new TreeSet<>(layers.stream().map(JavaLayer::id).toList()));
    }

    private static Set<DependencyKind> kinds(Set<DependencyKind> values) {
        Objects.requireNonNull(values, "includedKinds");
        return values.isEmpty()
                ? Set.of()
                : java.util.Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static String kindKey(Set<DependencyKind> kinds) {
        return kinds.stream().map(Enum::name).sorted().toList().toString();
    }

    private enum Mode {
        NO_ACCESS,
        MAY_ONLY_ACCESS,
        ONLY_ACCESSED_BY,
        ISOLATED
    }

    private record DirectedPair(LayerId origin, LayerId target)
            implements Comparable<DirectedPair> {
        private DirectedPair {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(target, "target");
            if (origin.equals(target)) throw new IllegalArgumentException("Layer pair must be distinct");
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

    private record LayerGraphView(Map<DirectedPair, EdgeGroup> edges) {
        private LayerGraphView {
            edges = Map.copyOf(new TreeMap<>(Objects.requireNonNull(edges, "edges")));
        }
    }
}
