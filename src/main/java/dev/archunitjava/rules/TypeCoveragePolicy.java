package dev.archunitjava.rules;

import dev.archunitjava.selector.TypeSelector;
import java.util.Objects;

/** A named, inspectable policy assignment for imported Java types. */
public record TypeCoveragePolicy(String name, TypeSelector selector) {
    public TypeCoveragePolicy {
        name = requireName(name);
        Objects.requireNonNull(selector, "selector");
    }

    public static TypeCoveragePolicy named(String name, TypeSelector selector) {
        return new TypeCoveragePolicy(name, selector);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("policy name must not be blank");
        }
        return value.trim();
    }
}
