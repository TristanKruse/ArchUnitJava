package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** An unresolved dynamic constant with bounded bootstrap provenance. */
public record JavaDynamicConstant(
        String name,
        JvmType constantType,
        JavaMethodHandle bootstrapMethod,
        List<JavaBootstrapArgument> bootstrapArguments,
        int originalBootstrapArgumentCount,
        boolean bootstrapArgumentsTruncated)
        implements Comparable<JavaDynamicConstant> {
    public static final int MAXIMUM_BOOTSTRAP_ARGUMENTS = 256;

    public JavaDynamicConstant {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        Objects.requireNonNull(constantType, "constantType");
        if (constantType instanceof JvmVoidType) {
            throw new IllegalArgumentException("A dynamic constant cannot have void type");
        }
        Objects.requireNonNull(bootstrapMethod, "bootstrapMethod");
        Objects.requireNonNull(bootstrapArguments, "bootstrapArguments");
        bootstrapArguments = bootstrapArguments.stream()
                .map(value -> Objects.requireNonNull(value, "bootstrapArgument"))
                .limit(MAXIMUM_BOOTSTRAP_ARGUMENTS)
                .toList();
        if (originalBootstrapArgumentCount < bootstrapArguments.size()) {
            throw new IllegalArgumentException("Original bootstrap argument count is too small");
        }
        if (bootstrapArgumentsTruncated
                != (originalBootstrapArgumentCount > bootstrapArguments.size())) {
            throw new IllegalArgumentException("Bootstrap argument truncation flag is inconsistent");
        }
    }

    @Override
    public int compareTo(JavaDynamicConstant other) {
        int result = name.compareTo(other.name);
        if (result != 0) return result;
        result = constantType.descriptor().compareTo(other.constantType.descriptor());
        return result != 0 ? result : bootstrapMethod.compareTo(other.bootstrapMethod);
    }
}
