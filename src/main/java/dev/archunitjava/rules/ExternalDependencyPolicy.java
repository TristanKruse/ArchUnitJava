package dev.archunitjava.rules;

/** Treatment of graph targets absent from the imported type/package model. */
public enum ExternalDependencyPolicy {
    IGNORE,
    TREAT_AS_NON_MATCHING,
    FAIL
}
