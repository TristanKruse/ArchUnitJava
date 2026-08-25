package dev.archunitjava.importer;

import java.util.Objects;
import java.util.Optional;

/** One InnerClasses table entry, retained as evidence rather than source-level truth. */
public record ParsedInnerClass(
        String innerBinaryName,
        Optional<String> outerBinaryName,
        Optional<String> innerSimpleName,
        int accessFlags)
        implements Comparable<ParsedInnerClass> {
    public ParsedInnerClass {
        innerBinaryName = binaryName(innerBinaryName, "innerBinaryName");
        Objects.requireNonNull(outerBinaryName, "outerBinaryName");
        outerBinaryName = outerBinaryName.map(value -> binaryName(value, "outerBinaryName"));
        Objects.requireNonNull(innerSimpleName, "innerSimpleName");
        innerSimpleName = innerSimpleName.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("innerSimpleName must not be blank");
            return value;
        });
    }

    private static String binaryName(String value, String role) {
        if (value == null || value.isBlank() || value.indexOf('/') >= 0) {
            throw new IllegalArgumentException(role + " must be a Java binary name");
        }
        return value;
    }

    @Override
    public int compareTo(ParsedInnerClass other) {
        int result = innerBinaryName.compareTo(other.innerBinaryName);
        if (result != 0) return result;
        result = outerBinaryName.orElse("").compareTo(other.outerBinaryName.orElse(""));
        if (result != 0) return result;
        result = innerSimpleName.orElse("").compareTo(other.innerSimpleName.orElse(""));
        return result != 0 ? result : Integer.compareUnsigned(accessFlags, other.accessFlags);
    }
}
