package dev.archunitjava.importer;

import java.util.Objects;
import java.util.Optional;

/** Backend-neutral record component extracted from a Record attribute. */
public record ParsedRecordComponent(
        String name, String descriptor, Optional<String> genericSignature)
        implements Comparable<ParsedRecordComponent> {
    public ParsedRecordComponent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
        Objects.requireNonNull(genericSignature, "genericSignature");
        genericSignature = genericSignature.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("genericSignature must not be blank");
            return value;
        });
    }

    @Override
    public int compareTo(ParsedRecordComponent other) {
        int result = name.compareTo(other.name);
        return result != 0 ? result : descriptor.compareTo(other.descriptor);
    }
}
