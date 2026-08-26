package dev.archunitjava.selector;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

final class SelectorDescriptions {
    private SelectorDescriptions() {}

    static SelectorDescription group(String operator, Collection<SelectorDescription> descriptions) {
        List<String> stable = descriptions.stream()
                .map(value -> Objects.requireNonNull(value, "description").text())
                .sorted()
                .toList();
        if (stable.isEmpty()) {
            throw new IllegalArgumentException(operator + " group must contain at least one selector");
        }
        return new SelectorDescription("(" + String.join(" " + operator + " ", stable) + ")");
    }

    static SelectorDescription not(SelectorDescription description) {
        return new SelectorDescription("NOT (" + description.text() + ")");
    }

    static SelectorDescription excluding(
            SelectorDescription base, SelectorDescription exclusion) {
        return new SelectorDescription(
                "(" + base.text() + " EXCLUDING " + exclusion.text() + ")");
    }
}
