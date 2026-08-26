package dev.archunitjava.rules;

/** Whether an edge whose projected origin equals its target participates in a rule. */
public enum SelfDependencyPolicy {
    INCLUDE,
    IGNORE
}
