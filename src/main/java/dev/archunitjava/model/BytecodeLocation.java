package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.MemberId;

/** Exact bytecode evidence location with optional, non-fabricated debug metadata. */
public record BytecodeLocation(
        ClassResourceLocation resource,
        Optional<SourceFileName> sourceFile,
        int bytecodeOffset,
        OptionalInt lineNumber) {
    public BytecodeLocation {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(sourceFile, "sourceFile");
        if (bytecodeOffset < 0) throw new IllegalArgumentException("bytecodeOffset must not be negative");
        Objects.requireNonNull(lineNumber, "lineNumber");
    }

    /** Converts this location into graph evidence while retaining absent debug metadata. */
    public DependencyEvidence dependencyEvidence(MemberId ownerMember) {
        return new DependencyEvidence(
                resource.locationId(),
                Optional.of(Objects.requireNonNull(ownerMember, "ownerMember")),
                OptionalInt.of(bytecodeOffset),
                sourceFile.map(SourceFileName::value),
                lineNumber);
    }
}
