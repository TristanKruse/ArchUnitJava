package dev.archunitjava.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** An immutable graph whose nodes, edges, and evidence have canonical iteration order. */
public final class DependencyGraph {
    private final List<GraphNode> nodes;
    private final List<DependencyEdge> edges;

    private DependencyGraph(List<GraphNode> nodes, List<DependencyEdge> edges) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
    }

    public static Builder builder() { return new Builder(); }
    public List<GraphNode> nodes() { return nodes; }
    public List<DependencyEdge> edges() { return edges; }

    @Override public boolean equals(Object other) {
        return other instanceof DependencyGraph graph
                && nodes.equals(graph.nodes) && edges.equals(graph.edges);
    }

    @Override public int hashCode() { return Objects.hash(nodes, edges); }

    public static final class Builder {
        private final Map<String, StableId> nodes = new TreeMap<>();
        private final Map<EdgeKey, Set<DependencyEvidence>> dependencies = new TreeMap<>();

        public Builder addNode(StableId id) {
            Objects.requireNonNull(id, "id");
            StableId previous = nodes.putIfAbsent(id.stableKey(), id);
            if (previous != null && !previous.equals(id)) {
                throw new IllegalArgumentException("Stable key collision: " + id.stableKey());
            }
            return this;
        }

        public Builder addDependency(StableId origin, StableId target, DependencyKind kind,
                DependencyEvidence evidence) {
            EdgeKey key = new EdgeKey(Objects.requireNonNull(origin), Objects.requireNonNull(target),
                    Objects.requireNonNull(kind));
            dependencies.computeIfAbsent(key, ignored -> new TreeSet<>())
                    .add(Objects.requireNonNull(evidence));
            return this;
        }

        public DependencyGraph build() {
            for (EdgeKey key : dependencies.keySet()) {
                requireKnown(key.origin);
                requireKnown(key.target);
            }
            List<GraphNode> graphNodes = nodes.values().stream().map(GraphNode::new).sorted().toList();
            List<DependencyEdge> graphEdges = new ArrayList<>();
            dependencies.forEach((key, evidence) -> graphEdges.add(new DependencyEdge(
                    key.origin, key.target, key.kind, List.copyOf(evidence))));
            return new DependencyGraph(graphNodes, graphEdges);
        }

        private void requireKnown(StableId id) {
            if (!nodes.containsKey(id.stableKey())) {
                throw new GraphValidationException("Unknown graph endpoint: " + id.stableKey());
            }
        }
    }

    private record EdgeKey(StableId origin, StableId target, DependencyKind kind)
            implements Comparable<EdgeKey> {
        @Override public int compareTo(EdgeKey other) {
            int result = origin.compareTo(other.origin);
            if (result != 0) return result;
            result = target.compareTo(other.target);
            return result != 0 ? result : kind.compareTo(other.kind);
        }
    }
}
