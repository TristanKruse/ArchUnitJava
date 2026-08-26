package dev.archunitjava.rules;

/** Java hierarchy relationship kept distinct instead of being flattened into dependencies. */
public enum HierarchyRelation {
    EXTENDS,
    IMPLEMENTS,
    ASSIGNABLE_TO,
    PERMITS
}
