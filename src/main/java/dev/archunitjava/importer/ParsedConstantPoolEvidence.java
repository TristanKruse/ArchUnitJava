package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** Bounded relevant constant-pool evidence for one class file. */
public record ParsedConstantPoolEvidence(
        List<ParsedConstantEvidence> constants, int originalEvidenceCount, boolean truncated) {
    public static final int MAXIMUM_CONSTANTS = 4096;

    public ParsedConstantPoolEvidence {
        Objects.requireNonNull(constants, "constants");
        constants = constants.stream()
                .map(value -> Objects.requireNonNull(value, "constant"))
                .sorted()
                .limit(MAXIMUM_CONSTANTS)
                .toList();
        if (originalEvidenceCount < constants.size()) {
            throw new IllegalArgumentException("Original evidence count is too small");
        }
        if (truncated != (originalEvidenceCount > constants.size())) {
            throw new IllegalArgumentException("Constant-pool truncation flag is inconsistent");
        }
    }

    public static ParsedConstantPoolEvidence empty() {
        return new ParsedConstantPoolEvidence(List.of(), 0, false);
    }
}
