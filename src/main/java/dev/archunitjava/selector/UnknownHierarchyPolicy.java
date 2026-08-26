package dev.archunitjava.selector;

/** Caller-selected treatment of a semantic match that incomplete model evidence cannot decide. */
public enum UnknownHierarchyPolicy {
    INCLUDE,
    EXCLUDE,
    FAIL
}
