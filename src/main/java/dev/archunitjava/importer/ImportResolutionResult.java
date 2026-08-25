package dev.archunitjava.importer;

import dev.archunitjava.model.ExternalTypeStub;
import dev.archunitjava.model.TypeModelResult;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Complete static import result with selected, external, and diagnostic state separated. */
public record ImportResolutionResult(
        ClassPathAssemblyResult assembly,
        TypeModelResult model,
        List<ResolvedImportedType> importedTypes,
        List<ExternalTypeStub> externalTypes,
        List<ImportResolutionDiagnostic> diagnostics) {
    public ImportResolutionResult {
        Objects.requireNonNull(assembly, "assembly");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(importedTypes, "importedTypes");
        importedTypes = importedTypes.stream()
                .map(value -> Objects.requireNonNull(value, "importedType"))
                .sorted()
                .toList();
        Objects.requireNonNull(externalTypes, "externalTypes");
        externalTypes = externalTypes.stream()
                .map(value -> Objects.requireNonNull(value, "externalType"))
                .sorted(Comparator.comparing(value -> value.name().binaryName()))
                .toList();
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = diagnostics.stream()
                .map(value -> Objects.requireNonNull(value, "diagnostic"))
                .sorted()
                .toList();
    }
}
