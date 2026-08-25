package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** Immutable deterministic output of Java resource lookup assembly. */
public record ClassPathAssemblyResult(
        List<SelectedClassResource> selections, List<InputDiagnostic> diagnostics) {
    public ClassPathAssemblyResult {
        Objects.requireNonNull(selections, "selections");
        selections = selections.stream()
                .map(value -> Objects.requireNonNull(value, "selection"))
                .sorted()
                .toList();
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = diagnostics.stream()
                .map(value -> Objects.requireNonNull(value, "diagnostic"))
                .sorted()
                .toList();
    }

    public List<ClassFileResource> selectedResources() {
        return selections.stream().map(SelectedClassResource::winner).toList();
    }
}
