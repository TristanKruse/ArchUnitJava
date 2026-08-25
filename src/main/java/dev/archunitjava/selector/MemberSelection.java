package dev.archunitjava.selector;

import dev.archunitjava.importer.ClassFileDiagnostic;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.TypeModelDiagnostic;
import java.util.List;
import java.util.Objects;

/** Selected members with overload-safe identities and retained import diagnostics. */
public record MemberSelection(
        SelectorDescription selector,
        int examinedCount,
        List<JavaMember> selected,
        List<ClassFileDiagnostic> classFileDiagnostics,
        List<TypeModelDiagnostic> modelDiagnostics,
        List<SelectionDiagnostic> selectionDiagnostics) {
    public MemberSelection {
        Objects.requireNonNull(selector, "selector");
        if (examinedCount < 0) throw new IllegalArgumentException("examinedCount must not be negative");
        selected = sorted(selected, "selected member");
        classFileDiagnostics = sorted(classFileDiagnostics, "class-file diagnostic");
        modelDiagnostics = sorted(modelDiagnostics, "model diagnostic");
        selectionDiagnostics = sorted(selectionDiagnostics, "selection diagnostic");
        if (selected.size() > examinedCount) {
            throw new IllegalArgumentException("selected count exceeds examined count");
        }
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public boolean importWasIncomplete() {
        return !classFileDiagnostics.isEmpty()
                || !modelDiagnostics.isEmpty()
                || !selectionDiagnostics.isEmpty();
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, role))
                .distinct()
                .sorted()
                .toList();
    }
}
