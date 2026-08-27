package dev.archunitjava.rules;

/** Whether declaration/signature and other non-call type-use edges participate in cycle rules. */
public enum TypeUseDependencyPolicy {
    IGNORE,
    INCLUDE
}
