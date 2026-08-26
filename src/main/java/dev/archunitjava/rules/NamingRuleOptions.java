package dev.archunitjava.rules;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Explicit opt-ins for subjects commonly created by compilers or generators. */
public record NamingRuleOptions(
        boolean includeAnonymousTypes,
        boolean includeLocalTypes,
        boolean includeGeneratedSubjects,
        Set<String> generatedAnnotationBinaryNames) {
    public NamingRuleOptions {
        Objects.requireNonNull(generatedAnnotationBinaryNames, "generatedAnnotationBinaryNames");
        TreeSet<String> names = new TreeSet<>();
        for (String name : generatedAnnotationBinaryNames) {
            if (name == null || name.isBlank() || name.indexOf('/') >= 0) {
                throw new IllegalArgumentException("generated annotation names must be binary names");
            }
            names.add(name);
        }
        generatedAnnotationBinaryNames = java.util.Collections.unmodifiableSet(names);
    }

    public static NamingRuleOptions defaults() {
        return new NamingRuleOptions(false, false, false, Set.of());
    }

    public NamingRuleOptions includingAnonymousTypes() {
        return new NamingRuleOptions(
                true, includeLocalTypes, includeGeneratedSubjects, generatedAnnotationBinaryNames);
    }

    public NamingRuleOptions includingLocalTypes() {
        return new NamingRuleOptions(
                includeAnonymousTypes, true, includeGeneratedSubjects, generatedAnnotationBinaryNames);
    }

    /** Includes generated types as well as synthetic or bridge members. */
    public NamingRuleOptions includingGeneratedSubjects() {
        return new NamingRuleOptions(
                includeAnonymousTypes, includeLocalTypes, true, generatedAnnotationBinaryNames);
    }

    /** Compatibility alias for the original type-oriented option name. */
    public NamingRuleOptions includingGeneratedTypes() {
        return includingGeneratedSubjects();
    }

    /** Compatibility accessor for the original type-oriented option name. */
    public boolean includeGeneratedTypes() {
        return includeGeneratedSubjects;
    }

    public NamingRuleOptions withGeneratedAnnotationBinaryNames(Set<String> names) {
        return new NamingRuleOptions(
                includeAnonymousTypes, includeLocalTypes, includeGeneratedSubjects, names);
    }
}
