package dev.archunitjava.importer;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Backend-neutral exception declaration, handler, or throw instruction. */
public record ParsedExceptionEvidence(
        Kind kind, Optional<String> targetDescriptor, OptionalInt bytecodeOffset)
        implements Comparable<ParsedExceptionEvidence> {
    public enum Kind {
        DECLARED_THROWS,
        CAUGHT_HANDLER,
        CATCH_ALL_HANDLER,
        THROW_INSTRUCTION
    }

    public ParsedExceptionEvidence {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetDescriptor, "targetDescriptor");
        targetDescriptor = targetDescriptor.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("targetDescriptor must not be blank");
            return value;
        });
        Objects.requireNonNull(bytecodeOffset, "bytecodeOffset");
        if (bytecodeOffset.isPresent() && bytecodeOffset.getAsInt() < 0) {
            throw new IllegalArgumentException("bytecodeOffset must not be negative");
        }
        boolean declared = kind == Kind.DECLARED_THROWS;
        boolean typed = kind == Kind.DECLARED_THROWS || kind == Kind.CAUGHT_HANDLER;
        if (typed != targetDescriptor.isPresent()) {
            throw new IllegalArgumentException("Exception evidence target does not match its kind");
        }
        if (declared == bytecodeOffset.isPresent()) {
            throw new IllegalArgumentException("Only bytecode evidence has an offset");
        }
    }

    @Override
    public int compareTo(ParsedExceptionEvidence other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = Integer.compare(bytecodeOffset.orElse(-1), other.bytecodeOffset.orElse(-1));
        return result != 0 ? result : targetDescriptor.orElse("").compareTo(other.targetDescriptor.orElse(""));
    }
}
