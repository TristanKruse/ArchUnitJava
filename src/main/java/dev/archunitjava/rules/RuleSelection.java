package dev.archunitjava.rules;

import dev.archunitjava.selector.SelectorDescription;
import java.util.Objects;

/** One named selector/count pair inspected by the shared rule terminal. */
public record RuleSelection(String role, SelectorDescription selector, int selectedCount) {
    public RuleSelection {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role must not be blank");
        Objects.requireNonNull(selector, "selector");
        if (selectedCount < 0) throw new IllegalArgumentException("selectedCount must not be negative");
    }
}
