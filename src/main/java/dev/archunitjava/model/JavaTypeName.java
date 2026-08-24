package dev.archunitjava.model;

/** Canonical binary name plus honest top-level source-name derivations. */
public record JavaTypeName(String binaryName) implements Comparable<JavaTypeName> {
    public JavaTypeName {
        if (binaryName == null
                || binaryName.isBlank()
                || binaryName.startsWith(".")
                || binaryName.endsWith(".")
                || binaryName.contains("..")
                || binaryName.indexOf('/') >= 0
                || binaryName.indexOf('[') >= 0
                || binaryName.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Invalid Java binary name: " + binaryName);
        }
    }

    /** Source-style qualified name for a top-level type. Nested names are enriched later. */
    public String sourceName() {
        return binaryName;
    }

    public String packageName() {
        int separator = binaryName.lastIndexOf('.');
        return separator < 0 ? "" : binaryName.substring(0, separator);
    }

    public String simpleName() {
        int separator = binaryName.lastIndexOf('.');
        return binaryName.substring(separator + 1);
    }

    @Override
    public int compareTo(JavaTypeName other) {
        return binaryName.compareTo(other.binaryName);
    }
}
