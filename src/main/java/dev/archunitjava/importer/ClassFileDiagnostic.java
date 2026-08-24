package dev.archunitjava.importer;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Structured parser failure with resource, origin, and traversal context. */
public record ClassFileDiagnostic(
        ClassFileDiagnosticCode code,
        String resourceName,
        ClassFileOrigin origin,
        ClassFileTraversalPhase phase,
        Map<String, String> context)
        implements Comparable<ClassFileDiagnostic> {
    public ClassFileDiagnostic {
        Objects.requireNonNull(code, "code");
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(context, "context");
        TreeMap<String, String> sorted = new TreeMap<>();
        context.forEach((key, value) -> sorted.put(requireText(key), requireText(value)));
        context = Collections.unmodifiableMap(sorted);
    }

    @Override
    public int compareTo(ClassFileDiagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        result = resourceName.compareTo(other.resourceName);
        if (result != 0) return result;
        result = origin.compareTo(other.origin);
        if (result != 0) return result;
        result = phase.compareTo(other.phase);
        return result != 0 ? result : context.toString().compareTo(other.context.toString());
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Diagnostic text must not be blank");
        }
        return value;
    }
}
