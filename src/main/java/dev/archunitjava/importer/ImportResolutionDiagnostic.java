package dev.archunitjava.importer;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Stable packaging-resolution diagnostic. */
public record ImportResolutionDiagnostic(
        ImportResolutionDiagnosticCode code, String subject, Map<String, String> context)
        implements Comparable<ImportResolutionDiagnostic> {
    public ImportResolutionDiagnostic {
        Objects.requireNonNull(code, "code");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        Objects.requireNonNull(context, "context");
        TreeMap<String, String> sorted = new TreeMap<>();
        context.forEach((key, value) -> sorted.put(text(key), text(value)));
        context = Collections.unmodifiableMap(sorted);
    }

    @Override
    public int compareTo(ImportResolutionDiagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        result = subject.compareTo(other.subject);
        return result != 0 ? result : context.toString().compareTo(other.context.toString());
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Diagnostic text must not be blank");
        }
        return value;
    }
}
