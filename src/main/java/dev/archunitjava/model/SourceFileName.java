package dev.archunitjava.model;

import java.util.Optional;

/** A sanitized source-file label, never an absolute or parent-relative path. */
public record SourceFileName(String value) implements Comparable<SourceFileName> {
    public SourceFileName {
        if (value == null
                || value.isBlank()
                || value.contains("/")
                || value.contains("\\")
                || value.equals(".")
                || value.equals("..")
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("value must be a safe source-file name");
        }
    }

    public static Optional<SourceFileName> fromUntrusted(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String normalized = value.replace('\\', '/');
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        int separator = normalized.lastIndexOf('/');
        String candidate = separator < 0 ? normalized : normalized.substring(separator + 1);
        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SourceFileName(candidate));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(SourceFileName other) {
        return value.compareTo(other.value);
    }
}
