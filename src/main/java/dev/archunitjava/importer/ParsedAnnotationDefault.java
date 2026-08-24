package dev.archunitjava.importer;

import java.util.Objects;

/** Default annotation value attached to one annotation-interface method. */
public record ParsedAnnotationDefault(
        String methodName, String methodDescriptor, ParsedAnnotationValue value)
        implements Comparable<ParsedAnnotationDefault> {
    public ParsedAnnotationDefault {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (methodDescriptor == null || methodDescriptor.isBlank()) {
            throw new IllegalArgumentException("methodDescriptor must not be blank");
        }
        Objects.requireNonNull(value, "value");
    }

    @Override
    public int compareTo(ParsedAnnotationDefault other) {
        int result = methodName.compareTo(other.methodName);
        if (result != 0) return result;
        result = methodDescriptor.compareTo(other.methodDescriptor);
        return result != 0 ? result : value.compareTo(other.value);
    }
}
