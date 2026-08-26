package dev.archunitjava.execution;

/** Uniform rule-terminal response when a selector matches no subjects. */
public enum EmptySelectionPolicy {
    ALLOW,
    WARN,
    FAIL
}
