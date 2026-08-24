package dev.archunitjava.model;

import java.util.Objects;

/** Package ownership for a top-level type; an empty name denotes the unnamed package. */
public record TypeOwner(String packageName) implements Comparable<TypeOwner> {
    public TypeOwner {
        Objects.requireNonNull(packageName, "packageName");
        if (packageName.startsWith(".") || packageName.endsWith(".") || packageName.contains("..")) {
            throw new IllegalArgumentException("Invalid package name: " + packageName);
        }
    }

    @Override
    public int compareTo(TypeOwner other) {
        return packageName.compareTo(other.packageName);
    }
}
