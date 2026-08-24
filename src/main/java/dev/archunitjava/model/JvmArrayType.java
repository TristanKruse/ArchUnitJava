package dev.archunitjava.model;

import java.util.Objects;

/** Canonical multidimensional JVM array with a non-array, non-void element type. */
public record JvmArrayType(JvmType elementType, int dimensions) implements JvmType {
    public static final int MAXIMUM_DIMENSIONS = 255;

    public JvmArrayType {
        Objects.requireNonNull(elementType, "elementType");
        if (elementType instanceof JvmArrayType || elementType instanceof JvmVoidType) {
            throw new IllegalArgumentException("Array element must be a non-array, non-void JVM type");
        }
        if (dimensions < 1 || dimensions > MAXIMUM_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "dimensions must be between 1 and " + MAXIMUM_DIMENSIONS);
        }
    }

    @Override
    public String descriptor() {
        return "[".repeat(dimensions) + elementType.descriptor();
    }

    @Override
    public String displayName() {
        return elementType.displayName() + "[]".repeat(dimensions);
    }
}
