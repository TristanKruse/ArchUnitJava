package dev.archunitjava.model;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable opt-in settings for conservative generated-code classification. */
public record GeneratedCodeOptions(boolean enabled, Set<String> annotationBinaryNames) {
    public GeneratedCodeOptions {
        Objects.requireNonNull(annotationBinaryNames, "annotationBinaryNames");
        TreeSet<String> names = new TreeSet<>();
        for (String name : annotationBinaryNames) {
            if (name == null || name.isBlank() || name.indexOf('/') >= 0) {
                throw new IllegalArgumentException("annotation names must be binary names");
            }
            names.add(name);
        }
        annotationBinaryNames = java.util.Collections.unmodifiableSet(names);
    }

    public static GeneratedCodeOptions disabled() {
        return new GeneratedCodeOptions(false, Set.of());
    }

    public static GeneratedCodeOptions enabled(Set<String> annotationBinaryNames) {
        return new GeneratedCodeOptions(true, annotationBinaryNames);
    }
}
