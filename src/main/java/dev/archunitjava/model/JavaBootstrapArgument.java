package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** Ordered generic bootstrap argument with optional structured direct-handle data. */
public record JavaBootstrapArgument(
        String kind, String encodedValue, Optional<JavaMethodHandle> methodHandle) {
    public JavaBootstrapArgument {
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must not be blank");
        Objects.requireNonNull(encodedValue, "encodedValue");
        Objects.requireNonNull(methodHandle, "methodHandle");
    }
}
