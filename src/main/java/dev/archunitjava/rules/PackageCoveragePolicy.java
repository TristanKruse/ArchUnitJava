package dev.archunitjava.rules;

import dev.archunitjava.selector.PackageSelector;
import java.util.Objects;

/** A named, inspectable policy assignment for imported Java packages. */
public record PackageCoveragePolicy(String name, PackageSelector selector) {
    public PackageCoveragePolicy {
        name = requireName(name);
        Objects.requireNonNull(selector, "selector");
    }

    public static PackageCoveragePolicy named(String name, PackageSelector selector) {
        return new PackageCoveragePolicy(name, selector);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("policy name must not be blank");
        }
        return value.trim();
    }
}
