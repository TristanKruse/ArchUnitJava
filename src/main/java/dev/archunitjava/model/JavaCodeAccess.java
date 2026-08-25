package dev.archunitjava.model;

import java.util.Objects;

/** One field or method bytecode access with exact caller, opcode, and location. */
public record JavaCodeAccess(
        JavaMemberSignature caller,
        JavaCodeAccessTarget target,
        JavaCodeAccessKind kind,
        JavaCodeAccessOpcode opcode,
        boolean interfaceTarget,
        BytecodeLocation location)
        implements Comparable<JavaCodeAccess> {
    public JavaCodeAccess {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(opcode, "opcode");
        Objects.requireNonNull(location, "location");
        boolean fieldOpcode = switch (opcode) {
            case GETFIELD, GETSTATIC, PUTFIELD, PUTSTATIC -> true;
            default -> false;
        };
        if (fieldOpcode == target.method()) {
            throw new IllegalArgumentException("Opcode and target descriptor kind disagree");
        }
        if (fieldOpcode && interfaceTarget) {
            throw new IllegalArgumentException("Field accesses do not carry invocation interface evidence");
        }
        if ((kind == JavaCodeAccessKind.CONSTRUCTOR_CALL) != target.name().equals("<init>")) {
            throw new IllegalArgumentException("Constructor kind must match the target name");
        }
        JavaCodeAccessKind opcodeKind = switch (opcode) {
            case GETFIELD, GETSTATIC -> JavaCodeAccessKind.FIELD_READ;
            case PUTFIELD, PUTSTATIC -> JavaCodeAccessKind.FIELD_WRITE;
            default -> target.name().equals("<init>")
                    ? JavaCodeAccessKind.CONSTRUCTOR_CALL
                    : JavaCodeAccessKind.METHOD_CALL;
        };
        if (kind != opcodeKind) throw new IllegalArgumentException("Kind does not match opcode");
    }

    @Override
    public int compareTo(JavaCodeAccess other) {
        int result = caller.compareTo(other.caller);
        if (result != 0) return result;
        result = Integer.compare(location.bytecodeOffset(), other.location.bytecodeOffset());
        if (result != 0) return result;
        result = opcode.compareTo(other.opcode);
        return result != 0 ? result : target.compareTo(other.target);
    }
}
