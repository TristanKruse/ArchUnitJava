package dev.archunitjava.rules;

import dev.archunitjava.pattern.PatternDomain;

/** Distinct Java naming and provenance values; each has one required pattern domain. */
public enum NamingTarget {
    SIMPLE_NAME(PatternDomain.QUALIFIED_NAME),
    BINARY_NAME(PatternDomain.QUALIFIED_NAME),
    PACKAGE_NAME(PatternDomain.QUALIFIED_NAME),
    SOURCE_FILE(PatternDomain.RESOURCE_PATH),
    CLASS_RESOURCE(PatternDomain.RESOURCE_PATH),
    ARTIFACT_CONTAINER(PatternDomain.RESOURCE_PATH);

    private final PatternDomain patternDomain;

    NamingTarget(PatternDomain patternDomain) {
        this.patternDomain = patternDomain;
    }

    public PatternDomain patternDomain() {
        return patternDomain;
    }
}
