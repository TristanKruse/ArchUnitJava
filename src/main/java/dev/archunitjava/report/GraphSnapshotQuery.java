package dev.archunitjava.report;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.ModuleId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.LayerId;
import dev.archunitjava.slices.SliceId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable filtering and collapse query that produces one detached graph snapshot. */
public final class GraphSnapshotQuery {
    private final DependencyGraph graph;
    private final ReportDomain domain;
    private final Map<String, Mapping> mappings;
    private final Set<DependencyKind> includedKinds;
    private final Set<String> includedNodeIds;
    private final Set<String> excludedNodeIds;
    private final boolean retainSelfEdges;
    private final GraphSnapshotLimits limits;

    private GraphSnapshotQuery(
            DependencyGraph graph,
            ReportDomain domain,
            Map<? extends StableId, ? extends StableId> mappings,
            Set<DependencyKind> includedKinds,
            Set<String> includedNodeIds,
            Set<String> excludedNodeIds,
            boolean retainSelfEdges,
            GraphSnapshotLimits limits) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.mappings = mappings(domain, mappings);
        this.includedKinds = kinds(includedKinds);
        this.includedNodeIds = Set.copyOf(new TreeSet<>(includedNodeIds));
        this.excludedNodeIds = Set.copyOf(new TreeSet<>(excludedNodeIds));
        this.retainSelfEdges = retainSelfEdges;
        this.limits = Objects.requireNonNull(limits, "limits");
        if (!java.util.Collections.disjoint(this.includedNodeIds, this.excludedNodeIds)) {
            throw new IllegalArgumentException("A report node cannot be both included and excluded");
        }
    }

    public static GraphSnapshotQuery packages(DependencyGraph graph) {
        return create(graph, ReportDomain.PACKAGE, Map.of());
    }

    public static GraphSnapshotQuery types(DependencyGraph graph) {
        return create(graph, ReportDomain.TYPE, Map.of());
    }

    public static GraphSnapshotQuery members(DependencyGraph graph) {
        return create(graph, ReportDomain.MEMBER, Map.of());
    }

    public static GraphSnapshotQuery layers(
            DependencyGraph graph, Map<? extends StableId, LayerId> mappings) {
        return create(graph, ReportDomain.LAYER, mappings);
    }

    public static GraphSnapshotQuery slices(
            DependencyGraph graph, Map<? extends StableId, SliceId> mappings) {
        return create(graph, ReportDomain.SLICE, mappings);
    }

    public static GraphSnapshotQuery artifacts(
            DependencyGraph graph, Map<? extends StableId, LocationId> mappings) {
        return create(graph, ReportDomain.ARTIFACT, mappings);
    }

    public static GraphSnapshotQuery modules(
            DependencyGraph graph, Map<? extends StableId, ModuleId> mappings) {
        return create(graph, ReportDomain.MODULE, mappings);
    }

    public GraphSnapshotQuery includingKinds(DependencyKind... kinds) {
        Objects.requireNonNull(kinds, "kinds");
        return includingKinds(List.of(kinds.clone()));
    }

    public GraphSnapshotQuery includingKinds(Collection<DependencyKind> kinds) {
        Objects.requireNonNull(kinds, "kinds");
        EnumSet<DependencyKind> values = EnumSet.noneOf(DependencyKind.class);
        kinds.forEach(value -> values.add(Objects.requireNonNull(value, "kind")));
        return copy(values, includedNodeIds, excludedNodeIds, retainSelfEdges, limits);
    }

    public GraphSnapshotQuery includingNodes(Collection<? extends StableId> nodes) {
        return copy(
                includedKinds,
                ids(nodes, "included node"),
                excludedNodeIds,
                retainSelfEdges,
                limits);
    }

    public GraphSnapshotQuery excludingNodes(Collection<? extends StableId> nodes) {
        return copy(
                includedKinds,
                includedNodeIds,
                ids(nodes, "excluded node"),
                retainSelfEdges,
                limits);
    }

    public GraphSnapshotQuery retainingSelfEdges(boolean value) {
        return copy(includedKinds, includedNodeIds, excludedNodeIds, value, limits);
    }

    public GraphSnapshotQuery limitedBy(GraphSnapshotLimits value) {
        return copy(includedKinds, includedNodeIds, excludedNodeIds, retainSelfEdges, value);
    }

    public GraphSnapshot snapshot() {
        TreeMap<String, MutableNode> collapsedNodes = new TreeMap<>();
        Map<String, StableId> collapsedBySource = new TreeMap<>();
        for (var node : graph.nodes()) {
            collapse(node.id()).ifPresent(collapsed -> {
                collapsedBySource.put(node.id().stableKey(), collapsed);
                if (selected(collapsed)) {
                    collapsedNodes.computeIfAbsent(
                                    collapsed.stableKey(), ignored -> new MutableNode(collapsed))
                            .add(node.id());
                }
            });
        }
        TreeMap<Pair, MutableEdge> collapsedEdges = new TreeMap<>();
        for (DependencyEdge edge : graph.edges()) {
            if (!includedKinds.contains(edge.kind())) continue;
            StableId origin = collapsedBySource.get(edge.origin().stableKey());
            StableId target = collapsedBySource.get(edge.target().stableKey());
            if (origin == null || target == null || !selected(origin) || !selected(target)) continue;
            if (!retainSelfEdges && origin.equals(target)) continue;
            collapsedEdges.computeIfAbsent(
                            new Pair(origin.stableKey(), target.stableKey()),
                            ignored -> new MutableEdge(origin, target))
                    .add(edge);
        }
        int matchedNodeCount = collapsedNodes.size();
        int matchedEdgeCount = collapsedEdges.size();
        Set<String> retainedNodeIds = collapsedNodes.keySet().stream()
                .limit(limits.maxNodes())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        List<SnapshotNode> nodes = collapsedNodes.entrySet().stream()
                .filter(entry -> retainedNodeIds.contains(entry.getKey()))
                .map(entry -> entry.getValue().freeze())
                .toList();
        List<Map.Entry<Pair, MutableEdge>> eligibleEdges = collapsedEdges.entrySet().stream()
                .filter(entry -> retainedNodeIds.contains(entry.getKey().origin())
                        && retainedNodeIds.contains(entry.getKey().target()))
                .limit(limits.maxEdges())
                .toList();
        List<SnapshotEdge> edges = eligibleEdges.stream()
                .map(entry -> entry.getValue().freeze(limits.maxEvidencePerEdge()))
                .toList();
        int omittedEvidence = edges.stream().mapToInt(SnapshotEdge::omittedEvidenceCount).sum();
        SnapshotQueryMetadata metadata = new SnapshotQueryMetadata(
                domain,
                includedKinds.stream().map(Enum::name).sorted().toList(),
                includedNodeIds.stream().sorted().toList(),
                excludedNodeIds.stream().sorted().toList(),
                retainSelfEdges,
                mappings.size(),
                graph.nodes().size(),
                graph.edges().size(),
                collapsedBySource.size(),
                matchedNodeCount,
                matchedEdgeCount,
                matchedNodeCount - nodes.size(),
                matchedEdgeCount - edges.size(),
                omittedEvidence,
                limits);
        return new GraphSnapshot(metadata, nodes, edges);
    }

    private Optional<StableId> collapse(StableId source) {
        return switch (domain) {
            case PACKAGE -> packageId(source).map(value -> value);
            case TYPE -> typeId(source).map(value -> value);
            case MEMBER -> source instanceof MemberId member
                    ? Optional.of(member) : Optional.empty();
            case LAYER, SLICE, ARTIFACT, MODULE -> Optional.ofNullable(mapped(source));
        };
    }

    private StableId mapped(StableId source) {
        Mapping exact = mappings.get(source.stableKey());
        if (exact != null) return exact.target();
        if (source instanceof MemberId member) {
            Mapping owner = mappings.get(member.owner().stableKey());
            if (owner != null) return owner.target();
        }
        return null;
    }

    private boolean selected(StableId id) {
        String key = id.stableKey();
        return (includedNodeIds.isEmpty() || includedNodeIds.contains(key))
                && !excludedNodeIds.contains(key);
    }

    private GraphSnapshotQuery copy(
            Set<DependencyKind> newKinds,
            Set<String> newIncludedNodes,
            Set<String> newExcludedNodes,
            boolean newRetainSelfEdges,
            GraphSnapshotLimits newLimits) {
        Map<StableId, StableId> mappingCopy = new java.util.LinkedHashMap<>();
        mappings.values().forEach(value -> mappingCopy.put(value.source(), value.target()));
        return new GraphSnapshotQuery(
                graph,
                domain,
                mappingCopy,
                newKinds,
                newIncludedNodes,
                newExcludedNodes,
                newRetainSelfEdges,
                newLimits);
    }

    private static GraphSnapshotQuery create(
            DependencyGraph graph,
            ReportDomain domain,
            Map<? extends StableId, ? extends StableId> mappings) {
        return new GraphSnapshotQuery(
                graph,
                domain,
                mappings,
                EnumSet.allOf(DependencyKind.class),
                Set.of(),
                Set.of(),
                false,
                GraphSnapshotLimits.defaults());
    }

    private static Map<String, Mapping> mappings(
            ReportDomain domain, Map<? extends StableId, ? extends StableId> values) {
        Objects.requireNonNull(values, "mappings");
        TreeMap<String, Mapping> result = new TreeMap<>();
        values.forEach((source, target) -> {
            StableId from = Objects.requireNonNull(source, "mapping source");
            StableId to = Objects.requireNonNull(target, "mapping target");
            requireTarget(domain, to);
            Mapping previous = result.putIfAbsent(from.stableKey(), new Mapping(from, to));
            if (previous != null && (!previous.source().equals(from) || !previous.target().equals(to))) {
                throw new IllegalArgumentException("Conflicting report mapping: " + from.stableKey());
            }
        });
        if (switch (domain) {
            case PACKAGE, TYPE, MEMBER -> !result.isEmpty();
            case LAYER, SLICE, ARTIFACT, MODULE -> false;
        }) {
            throw new IllegalArgumentException(domain + " uses built-in collapse and accepts no mappings");
        }
        return Map.copyOf(result);
    }

    private static void requireTarget(ReportDomain domain, StableId target) {
        boolean valid = switch (domain) {
            case PACKAGE -> target instanceof PackageId;
            case TYPE -> target instanceof TypeId;
            case MEMBER -> target instanceof MemberId;
            case LAYER -> target instanceof LayerId;
            case SLICE -> target instanceof SliceId;
            case ARTIFACT -> target instanceof LocationId;
            case MODULE -> target instanceof ModuleId;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Mapping target does not match " + domain + ": " + target.stableKey());
        }
    }

    private static Optional<PackageId> packageId(StableId source) {
        if (source instanceof PackageId value) return Optional.of(value);
        TypeId type = typeId(source).orElse(null);
        if (type == null) return Optional.empty();
        int separator = type.binaryName().lastIndexOf('.');
        return Optional.of(separator < 0
                ? PackageId.unnamed()
                : PackageId.named(type.binaryName().substring(0, separator)));
    }

    private static Optional<TypeId> typeId(StableId source) {
        if (source instanceof TypeId value) return Optional.of(value);
        return source instanceof MemberId member
                ? Optional.of(member.owner()) : Optional.empty();
    }

    private static Map<String, String> labels(StableId id) {
        return Map.of("label", switch (id) {
            case PackageId value -> value.isUnnamed() ? "<unnamed>" : value.qualifiedName();
            case TypeId value -> value.binaryName();
            case MemberId value -> value.owner().binaryName() + "#" + value.name() + value.descriptor();
            case LayerId value -> value.name();
            case SliceId value -> value.name();
            case LocationId value -> value.resourcePath();
            case ModuleId value -> value.name();
            default -> id.stableKey();
        });
    }

    private static Set<DependencyKind> kinds(Collection<DependencyKind> values) {
        Objects.requireNonNull(values, "includedKinds");
        if (values.isEmpty()) return Set.of();
        EnumSet<DependencyKind> result = EnumSet.noneOf(DependencyKind.class);
        values.forEach(value -> result.add(Objects.requireNonNull(value, "kind")));
        return java.util.Collections.unmodifiableSet(result);
    }

    private static Set<String> ids(Collection<? extends StableId> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, role).stableKey())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String edgeId(String origin, String target) {
        return digestId("report-edge:", origin, target);
    }

    private static String sourceEdgeId(DependencyEdge edge) {
        return digestId(
                "report-source-edge:",
                edge.origin().stableKey(),
                edge.target().stableKey(),
                edge.kind().name());
    }

    private static String digestId(String prefix, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) update(digest, value);
            return prefix + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK does not provide SHA-256", error);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[] {
                (byte) (bytes.length >>> 24),
                (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8),
                (byte) bytes.length
        });
        digest.update(bytes);
    }

    private record Mapping(StableId source, StableId target) {}

    private record Pair(String origin, String target) implements Comparable<Pair> {
        @Override
        public int compareTo(Pair other) {
            int result = origin.compareTo(other.origin);
            return result != 0 ? result : target.compareTo(other.target);
        }
    }

    private static final class MutableNode {
        private final StableId id;
        private final TreeSet<String> sourceIds = new TreeSet<>();

        private MutableNode(StableId id) {
            this.id = id;
        }

        private void add(StableId source) {
            sourceIds.add(source.stableKey());
        }

        private SnapshotNode freeze() {
            return new SnapshotNode(
                    id.stableKey(), labels(id).get("label"), sourceIds.size(), List.copyOf(sourceIds));
        }
    }

    private static final class MutableEdge {
        private final StableId origin;
        private final StableId target;
        private final TreeSet<String> kinds = new TreeSet<>();
        private final TreeSet<String> sourceEdges = new TreeSet<>();
        private final TreeSet<SnapshotEvidence> evidence = new TreeSet<>();

        private MutableEdge(StableId origin, StableId target) {
            this.origin = origin;
            this.target = target;
        }

        private void add(DependencyEdge edge) {
            kinds.add(edge.kind().name());
            sourceEdges.add(sourceEdgeId(edge));
            edge.evidence().stream().map(SnapshotEvidence::from).forEach(evidence::add);
        }

        private SnapshotEdge freeze(int maxEvidence) {
            List<SnapshotEvidence> retained = evidence.stream().limit(maxEvidence).toList();
            return new SnapshotEdge(
                    edgeId(origin.stableKey(), target.stableKey()),
                    origin.stableKey(),
                    target.stableKey(),
                    List.copyOf(kinds),
                    sourceEdges.size(),
                    List.copyOf(sourceEdges),
                    evidence.size(),
                    retained,
                    evidence.size() - retained.size());
        }
    }
}
