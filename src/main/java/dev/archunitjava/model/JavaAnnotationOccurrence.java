package dev.archunitjava.model;

import java.util.Objects;

/** One annotation and its exact visibility and class-file site. */
public record JavaAnnotationOccurrence(
        AnnotationVisibility visibility, AnnotationSite site, JavaAnnotation annotation)
        implements Comparable<JavaAnnotationOccurrence> {
    public JavaAnnotationOccurrence {
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(annotation, "annotation");
    }

    @Override
    public int compareTo(JavaAnnotationOccurrence other) {
        return stableKey().compareTo(other.stableKey());
    }

    private String stableKey() {
        return site.stableKey() + ":" + visibility + ":" + annotation.stableKey();
    }
}
