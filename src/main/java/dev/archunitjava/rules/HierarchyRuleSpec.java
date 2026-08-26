package dev.archunitjava.rules;

import java.util.Objects;

/** Immutable relationship, traversal, mood, and unknown-ancestor policy. */
public record HierarchyRuleSpec(
        HierarchyRelation relation,
        HierarchyDepth depth,
        HierarchyRuleMode mode,
        UnknownInheritancePolicy unknownHierarchy) {
    public HierarchyRuleSpec {
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(unknownHierarchy, "unknownHierarchy");
        if (relation == HierarchyRelation.PERMITS && depth != HierarchyDepth.DIRECT) {
            throw new IllegalArgumentException("Permitted subclasses are always direct");
        }
    }

    public static HierarchyRuleSpec direct(
            HierarchyRelation relation, HierarchyRuleMode mode) {
        return strict(relation, HierarchyDepth.DIRECT, mode);
    }

    public static HierarchyRuleSpec transitive(
            HierarchyRelation relation, HierarchyRuleMode mode) {
        return strict(relation, HierarchyDepth.TRANSITIVE, mode);
    }

    public static HierarchyRuleSpec permitting(HierarchyRuleMode mode) {
        return strict(HierarchyRelation.PERMITS, HierarchyDepth.DIRECT, mode);
    }

    public HierarchyRuleSpec withUnknownHierarchy(UnknownInheritancePolicy value) {
        return new HierarchyRuleSpec(relation, depth, mode, value);
    }

    private static HierarchyRuleSpec strict(
            HierarchyRelation relation, HierarchyDepth depth, HierarchyRuleMode mode) {
        return new HierarchyRuleSpec(
                relation, depth, mode, UnknownInheritancePolicy.FAIL);
    }
}
