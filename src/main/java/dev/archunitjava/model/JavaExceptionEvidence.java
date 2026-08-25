package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** One exception declaration or bytecode fact, without inferred runtime types. */
public record JavaExceptionEvidence(
        JavaMemberSignature owner,
        JavaExceptionEvidenceKind kind,
        Optional<JvmReferenceType> targetType,
        DeclarationLocation declarationLocation,
        Optional<BytecodeLocation> bytecodeLocation)
        implements Comparable<JavaExceptionEvidence> {
    public JavaExceptionEvidence {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(declarationLocation, "declarationLocation");
        Objects.requireNonNull(bytecodeLocation, "bytecodeLocation");
        boolean declared = kind == JavaExceptionEvidenceKind.DECLARED_THROWS;
        boolean typed = declared || kind == JavaExceptionEvidenceKind.CAUGHT_HANDLER;
        if (typed != targetType.isPresent()) {
            throw new IllegalArgumentException("Exception evidence target does not match its kind");
        }
        if (declared == bytecodeLocation.isPresent()) {
            throw new IllegalArgumentException("Only bytecode evidence has a bytecode location");
        }
    }

    @Override
    public int compareTo(JavaExceptionEvidence other) {
        int result = owner.compareTo(other.owner);
        if (result != 0) return result;
        result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = Integer.compare(
                bytecodeLocation.map(BytecodeLocation::bytecodeOffset).orElse(-1),
                other.bytecodeLocation.map(BytecodeLocation::bytecodeOffset).orElse(-1));
        return result != 0 ? result : targetType.map(JvmReferenceType::binaryName).orElse("")
                .compareTo(other.targetType.map(JvmReferenceType::binaryName).orElse(""));
    }
}
