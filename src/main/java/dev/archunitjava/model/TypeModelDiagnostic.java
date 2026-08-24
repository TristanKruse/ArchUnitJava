package dev.archunitjava.model;

import dev.archunitjava.importer.ClassFileOrigin;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Structured type-model adaptation diagnostic. */
public record TypeModelDiagnostic(
        TypeModelDiagnosticCode code,
        String resourceName,
        ClassFileOrigin origin,
        Map<String, String> context)
        implements Comparable<TypeModelDiagnostic> {
    public TypeModelDiagnostic {
        Objects.requireNonNull(code, "code");
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(context, "context");
        TreeMap<String, String> sorted = new TreeMap<>();
        context.forEach((key, value) -> sorted.put(requireText(key), requireText(value)));
        context = Collections.unmodifiableMap(sorted);
    }

    @Override
    public int compareTo(TypeModelDiagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        result = resourceName.compareTo(other.resourceName);
        if (result != 0) return result;
        result = origin.compareTo(other.origin);
        return result != 0 ? result : context.toString().compareTo(other.context.toString());
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Diagnostic text must not be blank");
        }
        return value;
    }
}
