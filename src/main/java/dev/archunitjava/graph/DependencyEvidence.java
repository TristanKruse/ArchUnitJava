package dev.archunitjava.graph;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Provenance for one observed dependency. Missing bytecode metadata remains explicit. */
public record DependencyEvidence(
        LocationId location,
        Optional<MemberId> ownerMember,
        OptionalInt bytecodeOffset,
        Optional<String> sourceFile,
        OptionalInt lineNumber) implements Comparable<DependencyEvidence> {
    public DependencyEvidence {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(ownerMember, "ownerMember");
        Objects.requireNonNull(bytecodeOffset, "bytecodeOffset");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(lineNumber, "lineNumber");
        if (bytecodeOffset.isPresent() && bytecodeOffset.getAsInt() < 0) {
            throw new IllegalArgumentException("Bytecode offset must not be negative");
        }
        if (sourceFile.isPresent() && sourceFile.orElseThrow().isBlank()) {
            throw new IllegalArgumentException("Source file must not be blank");
        }
        if (lineNumber.isPresent() && lineNumber.getAsInt() <= 0) {
            throw new IllegalArgumentException("Line number must be positive");
        }
    }

    public static DependencyEvidence at(LocationId location) {
        return new DependencyEvidence(location, Optional.empty(), OptionalInt.empty(),
                Optional.empty(), OptionalInt.empty());
    }

    public static DependencyEvidence at(LocationId location, MemberId ownerMember,
            int bytecodeOffset, int lineNumber) {
        return new DependencyEvidence(location, Optional.of(Objects.requireNonNull(ownerMember)),
                OptionalInt.of(bytecodeOffset), Optional.empty(), OptionalInt.of(lineNumber));
    }

    public static DependencyEvidence at(LocationId location, MemberId ownerMember,
            int bytecodeOffset, String sourceFile, int lineNumber) {
        return new DependencyEvidence(location, Optional.of(Objects.requireNonNull(ownerMember)),
                OptionalInt.of(bytecodeOffset), Optional.ofNullable(sourceFile), OptionalInt.of(lineNumber));
    }

    @Override
    public int compareTo(DependencyEvidence other) {
        int result = location.compareTo(other.location);
        if (result != 0) return result;
        result = optionalMemberKey(ownerMember).compareTo(optionalMemberKey(other.ownerMember));
        if (result != 0) return result;
        result = compare(bytecodeOffset, other.bytecodeOffset);
        if (result != 0) return result;
        result = sourceFile.orElse("").compareTo(other.sourceFile.orElse(""));
        return result != 0 ? result : compare(lineNumber, other.lineNumber);
    }

    private static String optionalMemberKey(Optional<MemberId> member) {
        return member.map(MemberId::stableKey).orElse("");
    }

    private static int compare(OptionalInt left, OptionalInt right) {
        if (left.isEmpty()) return right.isEmpty() ? 0 : -1;
        return right.isEmpty() ? 1 : Integer.compare(left.getAsInt(), right.getAsInt());
    }
}
