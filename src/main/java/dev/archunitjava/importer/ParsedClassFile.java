package dev.archunitjava.importer;

import java.util.Objects;

/** Backend-neutral class header extracted from an untrusted class resource. */
public record ParsedClassFile(
        String binaryName,
        int accessFlags,
        int majorVersion,
        int minorVersion,
        boolean moduleDescriptor,
        String resourceName,
        ClassFileOrigin origin,
        int precedence)
        implements Comparable<ParsedClassFile> {
    public ParsedClassFile {
        if (binaryName == null || binaryName.isBlank()) {
            throw new IllegalArgumentException("binaryName must not be blank");
        }
        if (majorVersion < 0 || minorVersion < 0) {
            throw new IllegalArgumentException("class-file versions must not be negative");
        }
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        Objects.requireNonNull(origin, "origin");
        if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
    }

    @Override
    public int compareTo(ParsedClassFile other) {
        int result = Integer.compare(precedence, other.precedence);
        if (result != 0) return result;
        result = binaryName.compareTo(other.binaryName);
        if (result != 0) return result;
        result = resourceName.compareTo(other.resourceName);
        return result != 0 ? result : origin.compareTo(other.origin);
    }
}
