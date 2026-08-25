package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** One invokedynamic instruction and its unresolved bootstrap evidence. */
public record ParsedDynamicCallSite(
        String invocationName,
        String invocationDescriptor,
        ParsedMethodHandle bootstrapMethod,
        List<ParsedBootstrapArgument> bootstrapArguments,
        int bytecodeOffset)
        implements Comparable<ParsedDynamicCallSite> {
    public ParsedDynamicCallSite {
        if (invocationName == null || invocationName.isBlank()) {
            throw new IllegalArgumentException("invocationName must not be blank");
        }
        if (invocationDescriptor == null || invocationDescriptor.isBlank()) {
            throw new IllegalArgumentException("invocationDescriptor must not be blank");
        }
        Objects.requireNonNull(bootstrapMethod, "bootstrapMethod");
        Objects.requireNonNull(bootstrapArguments, "bootstrapArguments");
        bootstrapArguments = List.copyOf(bootstrapArguments);
        bootstrapArguments.forEach(value -> Objects.requireNonNull(value, "bootstrapArgument"));
        if (bytecodeOffset < 0) throw new IllegalArgumentException("bytecodeOffset must not be negative");
    }

    @Override
    public int compareTo(ParsedDynamicCallSite other) {
        int result = Integer.compare(bytecodeOffset, other.bytecodeOffset);
        if (result != 0) return result;
        result = invocationName.compareTo(other.invocationName);
        return result != 0 ? result : invocationDescriptor.compareTo(other.invocationDescriptor);
    }
}
