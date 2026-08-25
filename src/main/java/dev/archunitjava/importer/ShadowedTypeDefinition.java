package dev.archunitjava.importer;

import dev.archunitjava.model.JavaTypeName;
import java.util.Objects;

/** One parsed lower-precedence definition retained for conflict evidence. */
public record ShadowedTypeDefinition(
        JavaTypeName typeName,
        String resourceName,
        ClassFileOrigin origin,
        int precedence)
        implements Comparable<ShadowedTypeDefinition> {
    public ShadowedTypeDefinition {
        Objects.requireNonNull(typeName, "typeName");
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        Objects.requireNonNull(origin, "origin");
        if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
    }

    @Override
    public int compareTo(ShadowedTypeDefinition other) {
        int result = Integer.compare(precedence, other.precedence);
        if (result != 0) return result;
        result = typeName.compareTo(other.typeName);
        if (result != 0) return result;
        result = resourceName.compareTo(other.resourceName);
        return result != 0 ? result : origin.compareTo(other.origin);
    }
}
