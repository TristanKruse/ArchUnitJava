package dev.archunitjava.rules;

/** Whether evidence owned only by synthetic types, synthetic members, or bridge methods is used. */
public enum SyntheticEdgePolicy {
    IGNORE,
    INCLUDE
}
