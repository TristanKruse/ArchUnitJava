package dev.archunitjava.model;

import java.util.Objects;

/** A named Java package or the distinct unnamed package. */
public record JavaPackageName(String value) implements Comparable<JavaPackageName> {
    public JavaPackageName {
        Objects.requireNonNull(value, "value");
        if (value.startsWith(".")
                || value.endsWith(".")
                || value.contains("..")
                || value.indexOf('/') >= 0
                || value.indexOf(';') >= 0
                || value.indexOf('[') >= 0
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid Java package name: " + value);
        }
    }

    public static JavaPackageName named(String value) {
        JavaPackageName result = new JavaPackageName(value);
        if (result.isUnnamed()) throw new IllegalArgumentException("Named package must not be empty");
        return result;
    }

    public static JavaPackageName unnamed() {
        return new JavaPackageName("");
    }

    public boolean isUnnamed() {
        return value.isEmpty();
    }

    public String displayName() {
        return isUnnamed() ? "<unnamed>" : value;
    }

    @Override
    public int compareTo(JavaPackageName other) {
        return value.compareTo(other.value);
    }
}
