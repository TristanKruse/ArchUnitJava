package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A declared generic type parameter, including its class and interface bounds. */
public record GenericTypeParameter(
        String name,
        Optional<GenericType> classBound,
        List<GenericType> interfaceBounds) {
    public GenericTypeParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Type-parameter name must not be blank");
        }
        Objects.requireNonNull(classBound, "classBound");
        classBound.ifPresent(GenericTypeParameter::requireReferenceLike);
        Objects.requireNonNull(interfaceBounds, "interfaceBounds");
        interfaceBounds = interfaceBounds.stream()
                .map(value -> {
                    Objects.requireNonNull(value, "interfaceBound");
                    requireReferenceLike(value);
                    return value;
                })
                .toList();
    }

    private static void requireReferenceLike(GenericType type) {
        if (type instanceof GenericType.PrimitiveType || type instanceof GenericType.VoidType) {
            throw new IllegalArgumentException("Generic bounds must be reference-like types");
        }
    }
}
