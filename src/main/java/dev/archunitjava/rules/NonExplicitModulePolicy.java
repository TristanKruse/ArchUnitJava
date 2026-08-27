package dev.archunitjava.rules;

/** Explicit caller decision for automatic and unnamed modules, which have no Module attribute. */
public enum NonExplicitModulePolicy {
    REJECT,
    SKIP
}
