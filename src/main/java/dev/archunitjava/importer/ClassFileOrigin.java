package dev.archunitjava.importer;

import java.util.Objects;

/** Stable provenance for one class-file resource. */
public record ClassFileOrigin(ClassFileInput.Kind kind, String input, String entry)
        implements Comparable<ClassFileOrigin> {
    public ClassFileOrigin {
        Objects.requireNonNull(kind, "kind");
        input = requireText(input, "input");
        entry = requireText(entry, "entry");
    }

    @Override
    public int compareTo(ClassFileOrigin other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = input.compareTo(other.input);
        return result != 0 ? result : entry.compareTo(other.entry);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
