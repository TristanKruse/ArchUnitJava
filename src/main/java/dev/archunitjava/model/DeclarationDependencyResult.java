package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Declaration extraction output plus explicit normalization accounting. */
public record DeclarationDependencyResult(
        List<DeclarationDependency> dependencies,
        List<JavaTypeName> externalTargets,
        int primitiveReferencesIgnored,
        int duplicateSourcesCollapsed) {
    public DeclarationDependencyResult {
        Objects.requireNonNull(dependencies, "dependencies");
        dependencies = dependencies.stream()
                .map(value -> Objects.requireNonNull(value, "dependency"))
                .sorted()
                .toList();
        Objects.requireNonNull(externalTargets, "externalTargets");
        externalTargets = externalTargets.stream()
                .map(value -> Objects.requireNonNull(value, "externalTarget"))
                .distinct()
                .sorted()
                .toList();
        if (primitiveReferencesIgnored < 0 || duplicateSourcesCollapsed < 0) {
            throw new IllegalArgumentException("Extraction counts must not be negative");
        }
    }
}
