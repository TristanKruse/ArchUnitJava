package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** One bounded inconsistency or gap in declared sealed-hierarchy evidence. */
public record SealedHierarchyDiagnostic(
        SealedHierarchyDiagnosticCode code, Optional<JavaTypeName> relatedType)
        implements Comparable<SealedHierarchyDiagnostic> {
    public SealedHierarchyDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(relatedType, "relatedType");
    }

    @Override
    public int compareTo(SealedHierarchyDiagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        return relatedType.map(JavaTypeName::binaryName).orElse("")
                .compareTo(other.relatedType.map(JavaTypeName::binaryName).orElse(""));
    }
}
