package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Declared and observed evidence for one possibly sealed type. */
public record SealedHierarchyResult(
        JavaTypeName type,
        boolean sealed,
        List<JavaTypeName> declaredPermittedSubclasses,
        List<JavaTypeName> observedDirectSubclasses,
        List<JavaTypeName> missingPermittedSubclasses,
        List<SealedHierarchyDiagnostic> diagnostics,
        boolean complete) {
    public SealedHierarchyResult {
        Objects.requireNonNull(type, "type");
        declaredPermittedSubclasses = sorted(declaredPermittedSubclasses, "declaredPermittedSubclass");
        observedDirectSubclasses = sorted(observedDirectSubclasses, "observedDirectSubclass");
        missingPermittedSubclasses = sorted(missingPermittedSubclasses, "missingPermittedSubclass");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = diagnostics.stream()
                .map(value -> Objects.requireNonNull(value, "diagnostic"))
                .sorted()
                .toList();
    }

    private static List<JavaTypeName> sorted(List<JavaTypeName> values, String name) {
        Objects.requireNonNull(values, name + "s");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, name))
                .sorted()
                .toList();
    }
}
