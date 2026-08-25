package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** Unresolved CONSTANT_Dynamic value and its bounded bootstrap provenance. */
public record ParsedDynamicConstant(
        String name,
        String descriptor,
        ParsedMethodHandle bootstrapMethod,
        List<ParsedBootstrapArgument> bootstrapArguments,
        int originalBootstrapArgumentCount,
        boolean bootstrapArgumentsTruncated)
        implements Comparable<ParsedDynamicConstant> {
    public static final int MAXIMUM_BOOTSTRAP_ARGUMENTS = 256;

    public ParsedDynamicConstant {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
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
    public int compareTo(ParsedDynamicConstant other) {
        int result = name.compareTo(other.name);
        if (result != 0) return result;
        result = descriptor.compareTo(other.descriptor);
        return result != 0 ? result : bootstrapMethod.compareTo(other.bootstrapMethod);
    }
}
