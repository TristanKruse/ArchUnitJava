package dev.archunitjava.rules;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Explicit opt-ins for subjects commonly created by compilers or generators. */
public record NamingRuleOptions(
        boolean includeAnonymousTypes,
        boolean includeLocalTypes,
        boolean includeGeneratedTypes,
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
                true, includeLocalTypes, includeGeneratedTypes, generatedAnnotationBinaryNames);
    }

    public NamingRuleOptions includingLocalTypes() {
        return new NamingRuleOptions(
                includeAnonymousTypes, true, includeGeneratedTypes, generatedAnnotationBinaryNames);
    }

    public NamingRuleOptions includingGeneratedTypes() {
        return new NamingRuleOptions(
                includeAnonymousTypes, includeLocalTypes, true, generatedAnnotationBinaryNames);
    }

    public NamingRuleOptions withGeneratedAnnotationBinaryNames(Set<String> names) {
        return new NamingRuleOptions(
                includeAnonymousTypes, includeLocalTypes, includeGeneratedTypes, names);
    }
}
