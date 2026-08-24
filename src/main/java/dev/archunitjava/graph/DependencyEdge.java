package dev.archunitjava.graph;

import java.util.List;
import java.util.Objects;

/** One directed, typed dependency with all duplicate-free evidence. */
public record DependencyEdge(
        StableId origin, StableId target, DependencyKind kind, List<DependencyEvidence> evidence)
        implements Comparable<DependencyEdge> {
    public DependencyEdge {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }

    @Override public int compareTo(DependencyEdge other) {
        int result = origin.compareTo(other.origin);
        if (result != 0) return result;
        result = target.compareTo(other.target);
        return result != 0 ? result : kind.compareTo(other.kind);
    }
}
