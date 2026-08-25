package dev.archunitjava.importer;

import java.util.Objects;
import java.util.Optional;

/** Stable generic bootstrap argument plus an optional structured direct handle. */
public record ParsedBootstrapArgument(
        String kind, String encodedValue, Optional<ParsedMethodHandle> methodHandle)
        implements Comparable<ParsedBootstrapArgument> {
    public ParsedBootstrapArgument {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        Objects.requireNonNull(encodedValue, "encodedValue");
        Objects.requireNonNull(methodHandle, "methodHandle");
    }

    @Override
    public int compareTo(ParsedBootstrapArgument other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = encodedValue.compareTo(other.encodedValue);
        return result != 0 ? result : methodHandle.map(ParsedMethodHandle::toString).orElse("")
                .compareTo(other.methodHandle.map(ParsedMethodHandle::toString).orElse(""));
    }
}
