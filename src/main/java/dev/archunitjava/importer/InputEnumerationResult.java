package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Immutable deterministic output of class-file input enumeration. */
public record InputEnumerationResult(
        List<ClassFileResource> resources, List<InputDiagnostic> diagnostics) {
    public InputEnumerationResult {
        resources = sortedCopy(resources, "resource");
        diagnostics = sortedCopy(diagnostics, "diagnostic");
    }

    private static <T extends Comparable<? super T>> List<T> sortedCopy(
            List<T> values, String name) {
        Objects.requireNonNull(values, name + "s");
        TreeSet<T> sorted = new TreeSet<>();
        for (T value : values) sorted.add(Objects.requireNonNull(value, name));
        return List.copyOf(sorted);
    }
}
