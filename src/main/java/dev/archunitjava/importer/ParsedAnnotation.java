package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** Backend-neutral annotation descriptor and explicitly stored element values. */
public record ParsedAnnotation(String typeDescriptor, List<ParsedAnnotationElement> elements)
        implements Comparable<ParsedAnnotation> {
    public ParsedAnnotation {
        if (typeDescriptor == null || typeDescriptor.isBlank()) {
            throw new IllegalArgumentException("typeDescriptor must not be blank");
        }
        Objects.requireNonNull(elements, "elements");
        elements = elements.stream()
                .map(value -> Objects.requireNonNull(value, "element"))
                .sorted()
                .toList();
    }

    public String stableKey() {
        StringBuilder key = new StringBuilder(typeDescriptor).append(':');
        elements.forEach(element -> key.append(element.name().length())
                .append(':').append(element.name())
                .append(element.value().stableKey().length())
                .append(':').append(element.value().stableKey()));
        return key.toString();
    }

    @Override
    public int compareTo(ParsedAnnotation other) {
        return stableKey().compareTo(other.stableKey());
    }
}
