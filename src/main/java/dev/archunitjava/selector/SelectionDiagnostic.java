package dev.archunitjava.selector;

import java.util.Objects;

/** Deterministic evidence that selection encountered an incomplete imported model. */
public record SelectionDiagnostic(
        SelectionDiagnosticCode code, String subject, String detail)
        implements Comparable<SelectionDiagnostic> {
    public SelectionDiagnostic {
        Objects.requireNonNull(code, "code");
        subject = requireText(subject, "subject");
        detail = requireText(detail, "detail");
    }

    @Override
    public int compareTo(SelectionDiagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        result = subject.compareTo(other.subject);
        return result != 0 ? result : detail.compareTo(other.detail);
    }

    private static String requireText(String value, String role) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value;
    }
}
