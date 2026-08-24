package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** One exact or wildcard generic class type argument. */
public record GenericTypeArgument(Variance variance, Optional<GenericType> type) {
    public enum Variance {
        EXACT,
        EXTENDS,
        SUPER,
        UNBOUNDED
    }

    public GenericTypeArgument {
        Objects.requireNonNull(variance, "variance");
        Objects.requireNonNull(type, "type");
        if ((variance == Variance.UNBOUNDED) != type.isEmpty()) {
            throw new IllegalArgumentException("Only an unbounded wildcard omits its type");
        }
        type.ifPresent(value -> {
            if (value instanceof GenericType.PrimitiveType || value instanceof GenericType.VoidType) {
                throw new IllegalArgumentException("Generic arguments must be reference-like types");
            }
        });
    }

    public static GenericTypeArgument exact(GenericType type) {
        return new GenericTypeArgument(Variance.EXACT, Optional.of(type));
    }

    public static GenericTypeArgument extendsBound(GenericType type) {
        return new GenericTypeArgument(Variance.EXTENDS, Optional.of(type));
    }

    public static GenericTypeArgument superBound(GenericType type) {
        return new GenericTypeArgument(Variance.SUPER, Optional.of(type));
    }

    public static GenericTypeArgument unbounded() {
        return new GenericTypeArgument(Variance.UNBOUNDED, Optional.empty());
    }
}
