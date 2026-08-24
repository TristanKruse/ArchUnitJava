package dev.archunitjava.graph;

import java.util.Objects;

/** A node retained independently of whether any dependencies touch it. */
public record GraphNode(StableId id) implements Comparable<GraphNode> {
    public GraphNode { Objects.requireNonNull(id, "id"); }
    @Override public int compareTo(GraphNode other) { return id.compareTo(other.id); }
}
