package dev.archunitjava.importer;

import java.util.Objects;
import java.util.Optional;

/** Backend-neutral MethodParameters entry, including compiler-created flags. */
public record ParsedMethodParameter(
        int index, Optional<String> name, int accessFlags)
        implements Comparable<ParsedMethodParameter> {
    public ParsedMethodParameter {
        if (index < 0) throw new IllegalArgumentException("index must not be negative");
        Objects.requireNonNull(name, "name");
        name = name.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("parameter name must not be blank");
            return value;
        });
    }

    @Override
    public int compareTo(ParsedMethodParameter other) {
        return Integer.compare(index, other.index);
    }
}
