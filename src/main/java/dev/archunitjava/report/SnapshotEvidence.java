package dev.archunitjava.report;

import dev.archunitjava.graph.DependencyEvidence;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Detached evidence value retained for report drill-down. */
public record SnapshotEvidence(
        String locationId,
        Optional<String> ownerMemberId,
        OptionalInt bytecodeOffset,
        Optional<String> sourceFile,
        OptionalInt lineNumber)
        implements Comparable<SnapshotEvidence> {
    public SnapshotEvidence {
        locationId = text(locationId, "locationId");
        Objects.requireNonNull(ownerMemberId, "ownerMemberId");
        ownerMemberId = ownerMemberId.map(value -> text(value, "ownerMemberId"));
        Objects.requireNonNull(bytecodeOffset, "bytecodeOffset");
        Objects.requireNonNull(sourceFile, "sourceFile");
        sourceFile = sourceFile.map(value -> text(value, "sourceFile"));
        Objects.requireNonNull(lineNumber, "lineNumber");
    }

    static SnapshotEvidence from(DependencyEvidence evidence) {
        return new SnapshotEvidence(
                evidence.location().stableKey(),
                evidence.ownerMember().map(value -> value.stableKey()),
                evidence.bytecodeOffset(),
                evidence.sourceFile(),
                evidence.lineNumber());
    }

    public String stableKey() {
        return part(locationId) + part(ownerMemberId.orElse(""))
                + part(optional(bytecodeOffset)) + part(sourceFile.orElse(""))
                + part(optional(lineNumber));
    }

    @Override
    public int compareTo(SnapshotEvidence other) {
        int result = locationId.compareTo(other.locationId);
        if (result != 0) return result;
        result = ownerMemberId.orElse("").compareTo(other.ownerMemberId.orElse(""));
        if (result != 0) return result;
        result = compare(bytecodeOffset, other.bytecodeOffset);
        if (result != 0) return result;
        result = sourceFile.orElse("").compareTo(other.sourceFile.orElse(""));
        return result != 0 ? result : compare(lineNumber, other.lineNumber);
    }

    private static String optional(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "";
    }

    private static String part(String value) {
        return value.length() + ":" + value;
    }

    private static int compare(OptionalInt left, OptionalInt right) {
        if (left.isEmpty()) return right.isEmpty() ? 0 : -1;
        return right.isEmpty() ? 1 : Integer.compare(left.getAsInt(), right.getAsInt());
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
