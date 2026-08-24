package dev.archunitjava.graph;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** One directed, typed dependency with all duplicate-free evidence. */
public record DependencyEdge(
        StableId origin, StableId target, DependencyKind kind, List<DependencyEvidence> evidence)
        implements Comparable<DependencyEdge> {
    public DependencyEdge {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        evidence = List.copyOf(new TreeSet<>(Objects.requireNonNull(evidence, "evidence")));
    }

    @Override public int compareTo(DependencyEdge other) {
        int result = origin.compareTo(other.origin);
        if (result != 0) return result;
        result = target.compareTo(other.target);
        if (result != 0) return result;
        result = kind.compareTo(other.kind);
        if (result != 0) return result;
        int sharedSize = Math.min(evidence.size(), other.evidence.size());
        for (int index = 0; index < sharedSize; index++) {
            result = evidence.get(index).compareTo(other.evidence.get(index));
            if (result != 0) return result;
        }
        return Integer.compare(evidence.size(), other.evidence.size());
    }
}
