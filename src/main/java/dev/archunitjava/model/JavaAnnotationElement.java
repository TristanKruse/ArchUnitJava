package dev.archunitjava.model;

import java.util.Objects;

/** One explicitly stored annotation element. */
public record JavaAnnotationElement(String name, JavaAnnotationValue value)
        implements Comparable<JavaAnnotationElement> {
    public JavaAnnotationElement {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(value, "value");
    }

    @Override
    public int compareTo(JavaAnnotationElement other) {
        int result = name.compareTo(other.name);
        return result != 0 ? result : value.compareTo(other.value);
    }
}
