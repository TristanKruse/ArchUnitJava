package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Immutable result of reading one or more class-file resources. */
public final class ClassFileReadResult {
    private final List<ParsedClassFile> classes;
    private final List<ClassFileDiagnostic> diagnostics;

    public ClassFileReadResult(List<ParsedClassFile> classes, List<ClassFileDiagnostic> diagnostics) {
        this.classes = sorted(classes, "parsedClass");
        this.diagnostics = sorted(diagnostics, "diagnostic");
    }

    public List<ParsedClassFile> classes() {
        return classes;
    }

    public Optional<ParsedClassFile> parsedClass() {
        return classes.size() == 1 ? Optional.of(classes.getFirst()) : Optional.empty();
    }

    public List<ClassFileDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String name) {
        Objects.requireNonNull(values, name + "es");
        TreeSet<T> sorted = new TreeSet<>();
        for (T value : values) sorted.add(Objects.requireNonNull(value, name));
        return List.copyOf(sorted);
    }
}
