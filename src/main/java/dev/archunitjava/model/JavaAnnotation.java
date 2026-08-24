package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Annotation type and explicitly encoded values; defaults are kept on annotation methods. */
public record JavaAnnotation(JvmReferenceType type, List<JavaAnnotationElement> elements)
        implements Comparable<JavaAnnotation> {
    public JavaAnnotation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(elements, "elements");
        elements = elements.stream()
                .map(value -> Objects.requireNonNull(value, "element"))
                .sorted()
                .toList();
    }

    public String stableKey() {
        StringBuilder key = new StringBuilder(type.descriptor()).append(':');
        elements.forEach(element -> key.append(element.name().length())
                .append(':').append(element.name())
                .append(element.value().stableKey().length())
                .append(':').append(element.value().stableKey()));
        return key.toString();
    }

    @Override
    public int compareTo(JavaAnnotation other) {
        return stableKey().compareTo(other.stableKey());
    }
}
