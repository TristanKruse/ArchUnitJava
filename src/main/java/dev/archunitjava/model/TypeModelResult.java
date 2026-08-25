package dev.archunitjava.model;

import dev.archunitjava.importer.ClassFileDiagnostic;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Immutable type-model output, retaining reader and adaptation diagnostics separately. */
public record TypeModelResult(
        List<JavaType> types,
        List<JavaModule> modules,
        List<ClassFileDiagnostic> classFileDiagnostics,
        List<TypeModelDiagnostic> diagnostics) {
    public TypeModelResult {
        types = sorted(types, "type");
        modules = sorted(modules, "module");
        classFileDiagnostics = sorted(classFileDiagnostics, "classFileDiagnostic");
        diagnostics = sorted(diagnostics, "diagnostic");
    }

    public TypeModelResult(
            List<JavaType> types,
            List<ClassFileDiagnostic> classFileDiagnostics,
            List<TypeModelDiagnostic> diagnostics) {
        this(types, List.of(), classFileDiagnostics, diagnostics);
    }

    public JavaPackageIndex packages() {
        return JavaPackageIndex.of(types);
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String name) {
        Objects.requireNonNull(values, name + "s");
        TreeSet<T> sorted = new TreeSet<>();
        for (T value : values) sorted.add(Objects.requireNonNull(value, name));
        return List.copyOf(sorted);
    }
}
