package dev.archunitjava.model;

import java.util.Objects;

/** One direct, typed hierarchy relationship. */
public record HierarchyRelationship(
        JavaTypeName origin, JavaTypeName target, HierarchyRelationshipKind kind)
        implements Comparable<HierarchyRelationship> {
    public HierarchyRelationship {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
    }

    @Override
    public int compareTo(HierarchyRelationship other) {
        int result = origin.compareTo(other.origin);
        if (result != 0) return result;
        result = target.compareTo(other.target);
        return result != 0 ? result : kind.compareTo(other.kind);
    }
}
