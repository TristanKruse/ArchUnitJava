package dev.archunitjava.importer;

import java.util.Objects;

/** One named annotation element value. */
public record ParsedAnnotationElement(String name, ParsedAnnotationValue value)
        implements Comparable<ParsedAnnotationElement> {
    public ParsedAnnotationElement {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(value, "value");
    }

    @Override
    public int compareTo(ParsedAnnotationElement other) {
        int result = name.compareTo(other.name);
        return result != 0 ? result : value.compareTo(other.value);
    }
}
