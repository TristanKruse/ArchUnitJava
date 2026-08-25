package dev.archunitjava.projection;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.StableId;
import java.util.Objects;

/** Exact raw edge selector for an explicit, reviewable exclusion. */
public record ProjectionEdgeSelector(
        StableId origin, StableId target, DependencyKind kind)
        implements Comparable<ProjectionEdgeSelector> {
    public ProjectionEdgeSelector {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
    }

    public boolean matches(DependencyEdge edge) {
        Objects.requireNonNull(edge, "edge");
        return origin.equals(edge.origin()) && target.equals(edge.target()) && kind == edge.kind();
    }

    @Override
    public int compareTo(ProjectionEdgeSelector other) {
        int result = origin.compareTo(other.origin);
        if (result != 0) return result;
        result = target.compareTo(other.target);
        return result != 0 ? result : kind.compareTo(other.kind);
    }
}
