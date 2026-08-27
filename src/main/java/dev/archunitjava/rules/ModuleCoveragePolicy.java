package dev.archunitjava.rules;

import dev.archunitjava.selector.ModuleSelector;
import java.util.Objects;

/** A named, inspectable policy assignment for imported JPMS module identities. */
public record ModuleCoveragePolicy(String name, ModuleSelector selector) {
    public ModuleCoveragePolicy {
        name = requireName(name);
        Objects.requireNonNull(selector, "selector");
    }

    public static ModuleCoveragePolicy named(String name, ModuleSelector selector) {
        return new ModuleCoveragePolicy(name, selector);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("policy name must not be blank");
        }
        return value.trim();
    }
}
