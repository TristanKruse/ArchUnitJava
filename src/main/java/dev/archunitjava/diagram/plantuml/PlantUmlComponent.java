package dev.archunitjava.diagram.plantuml;

import java.util.Objects;
import java.util.Optional;

/** One safely parsed component with a unique alias and display name. */
public record PlantUmlComponent(
        String displayName, String alias, Optional<String> stereotype)
        implements Comparable<PlantUmlComponent> {
    public PlantUmlComponent {
        displayName = text(displayName, "displayName");
        alias = alias(alias);
        Objects.requireNonNull(stereotype, "stereotype");
        stereotype = stereotype.map(value -> alias(value));
    }

    @Override
    public int compareTo(PlantUmlComponent other) {
        return alias.compareTo(other.alias);
    }

    static String alias(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("alias must match [A-Za-z][A-Za-z0-9_-]*");
        }
        return value;
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(role + " must not be blank or contain NUL");
        }
        return value;
    }
}
