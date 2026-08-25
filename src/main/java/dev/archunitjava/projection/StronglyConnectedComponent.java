package dev.archunitjava.projection;

import dev.archunitjava.graph.StableId;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** One deterministic maximal set of mutually reachable graph nodes. */
public record StronglyConnectedComponent(List<StableId> nodes, boolean cyclic)
        implements Comparable<StronglyConnectedComponent> {
    public StronglyConnectedComponent {
        Objects.requireNonNull(nodes, "nodes");
        TreeSet<StableId> sorted = new TreeSet<>();
        for (StableId node : nodes) {
            if (!sorted.add(Objects.requireNonNull(node, "node"))) {
                throw new IllegalArgumentException("Component nodes must be unique");
            }
        }
        if (sorted.isEmpty()) throw new IllegalArgumentException("Component must not be empty");
        nodes = List.copyOf(sorted);
        if (nodes.size() > 1 && !cyclic) {
            throw new IllegalArgumentException("A multi-node strongly connected component is cyclic");
        }
    }

    @Override
    public int compareTo(StronglyConnectedComponent other) {
        return compareNodes(nodes, other.nodes);
    }

    static int compareNodes(List<StableId> first, List<StableId> second) {
        int shared = Math.min(first.size(), second.size());
        for (int index = 0; index < shared; index++) {
            int result = first.get(index).compareTo(second.get(index));
            if (result != 0) return result;
        }
        return Integer.compare(first.size(), second.size());
    }
}
