package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Bounded typed constant-pool evidence for one imported type. */
public record JavaConstantPoolEvidence(
        List<JavaConstantEvidence> constants, int originalEvidenceCount, boolean truncated) {
    public static final int MAXIMUM_CONSTANTS = 4096;

    public JavaConstantPoolEvidence {
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
}
