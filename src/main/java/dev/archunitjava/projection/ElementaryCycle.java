package dev.archunitjava.projection;

import dev.archunitjava.graph.StableId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A directed elementary cycle, stored once with its lowest stable node first. */
public record ElementaryCycle(List<StableId> nodes) implements Comparable<ElementaryCycle> {
    public ElementaryCycle {
        Objects.requireNonNull(nodes, "nodes");
        if (nodes.isEmpty()) throw new IllegalArgumentException("Cycle must not be empty");
        List<StableId> copy = nodes.stream()
                .map(value -> Objects.requireNonNull(value, "node"))
                .toList();
        Set<StableId> unique = new HashSet<>(copy);
        if (unique.size() != copy.size()) {
            throw new IllegalArgumentException("Elementary cycle nodes must be unique");
        }
        StableId lowest = copy.stream().min(StableId::compareTo).orElseThrow();
        int start = copy.indexOf(lowest);
        ArrayList<StableId> canonical = new ArrayList<>(copy.size());
        canonical.addAll(copy.subList(start, copy.size()));
        canonical.addAll(copy.subList(0, start));
        nodes = List.copyOf(canonical);
    }

    /** The diagnostic path including the closing occurrence of the first node. */
    public List<StableId> closedPath() {
        ArrayList<StableId> result = new ArrayList<>(nodes);
        result.add(nodes.getFirst());
        return List.copyOf(result);
    }

    @Override
    public int compareTo(ElementaryCycle other) {
        return StronglyConnectedComponent.compareNodes(nodes, other.nodes);
    }
}
