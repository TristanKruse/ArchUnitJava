package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** One provides directive and all provider binary names. */
public record ParsedModuleProvide(String serviceBinaryName, List<String> providerBinaryNames)
        implements Comparable<ParsedModuleProvide> {
    public ParsedModuleProvide {
        serviceBinaryName = name(serviceBinaryName, "serviceBinaryName");
        Objects.requireNonNull(providerBinaryNames, "providerBinaryNames");
        providerBinaryNames = providerBinaryNames.stream()
                .map(value -> name(value, "providerBinaryName"))
                .sorted()
                .toList();
        if (providerBinaryNames.isEmpty()) {
            throw new IllegalArgumentException("A provides directive needs at least one provider");
        }
    }

    @Override
    public int compareTo(ParsedModuleProvide other) {
        int result = serviceBinaryName.compareTo(other.serviceBinaryName);
        return result != 0 ? result : providerBinaryNames.toString()
                .compareTo(other.providerBinaryNames.toString());
    }

    private static String name(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
