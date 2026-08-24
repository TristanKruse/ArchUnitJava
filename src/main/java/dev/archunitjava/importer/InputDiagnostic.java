package dev.archunitjava.importer;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Structured enumeration diagnostic; renderers decide how it is presented. */
public record InputDiagnostic(InputDiagnosticCode code, String input, Map<String, String> context)
        implements Comparable<InputDiagnostic> {
    public InputDiagnostic {
        Objects.requireNonNull(code, "code");
        input = requireText(input);
        Objects.requireNonNull(context, "context");
        TreeMap<String, String> sorted = new TreeMap<>();
        context.forEach((key, value) -> sorted.put(requireText(key), requireText(value)));
        context = Collections.unmodifiableMap(sorted);
    }

    @Override
    public int compareTo(InputDiagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        result = input.compareTo(other.input);
        if (result != 0) return result;
        return context.toString().compareTo(other.context.toString());
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Diagnostic text must not be blank");
        }
        return value;
    }
}
