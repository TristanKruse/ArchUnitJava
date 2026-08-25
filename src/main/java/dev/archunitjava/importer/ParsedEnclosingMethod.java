package dev.archunitjava.importer;

import java.util.Objects;
import java.util.Optional;

/** One EnclosingMethod attribute value. */
public record ParsedEnclosingMethod(
        String enclosingClassBinaryName,
        Optional<String> methodName,
        Optional<String> methodDescriptor)
        implements Comparable<ParsedEnclosingMethod> {
    public ParsedEnclosingMethod {
        if (enclosingClassBinaryName == null
                || enclosingClassBinaryName.isBlank()
                || enclosingClassBinaryName.indexOf('/') >= 0) {
            throw new IllegalArgumentException("enclosingClassBinaryName must be a Java binary name");
        }
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(methodDescriptor, "methodDescriptor");
        if (methodName.isPresent() != methodDescriptor.isPresent()) {
            throw new IllegalArgumentException("Enclosing method name and descriptor must occur together");
        }
        methodName = methodName.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("methodName must not be blank");
            return value;
        });
        methodDescriptor = methodDescriptor.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("methodDescriptor must not be blank");
            return value;
        });
    }

    @Override
    public int compareTo(ParsedEnclosingMethod other) {
        int result = enclosingClassBinaryName.compareTo(other.enclosingClassBinaryName);
        if (result != 0) return result;
        result = methodName.orElse("").compareTo(other.methodName.orElse(""));
        return result != 0 ? result : methodDescriptor.orElse("")
                .compareTo(other.methodDescriptor.orElse(""));
    }
}
