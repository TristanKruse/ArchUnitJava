package dev.archunitjava.selector;

import dev.archunitjava.importer.ClassFileDiagnostic;
import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.TypeModelDiagnostic;
import java.util.List;
import java.util.Objects;

/** Selected JPMS descriptors with retained import/model diagnostics. */
public record ModuleSelection(
        SelectorDescription selector,
        int examinedCount,
        List<JavaModule> selected,
        List<ClassFileDiagnostic> classFileDiagnostics,
        List<TypeModelDiagnostic> modelDiagnostics) {
    public ModuleSelection {
        Objects.requireNonNull(selector, "selector");
        if (examinedCount < 0) throw new IllegalArgumentException("examinedCount must not be negative");
        selected = selected.stream()
                .map(value -> Objects.requireNonNull(value, "selected module"))
                .distinct().sorted().toList();
        classFileDiagnostics = classFileDiagnostics.stream().distinct().sorted().toList();
        modelDiagnostics = modelDiagnostics.stream().distinct().sorted().toList();
        if (selected.size() > examinedCount) {
            throw new IllegalArgumentException("selected count exceeds examined count");
        }
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }
}
