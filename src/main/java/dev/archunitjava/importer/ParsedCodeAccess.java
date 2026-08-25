package dev.archunitjava.importer;

/** Backend-neutral field or method access extracted from one Code attribute. */
public record ParsedCodeAccess(
        Kind kind,
        Opcode opcode,
        String targetOwnerDescriptor,
        String targetName,
        String targetDescriptor,
        boolean interfaceTarget,
        int bytecodeOffset)
        implements Comparable<ParsedCodeAccess> {
    public enum Kind {
        CONSTRUCTOR_CALL,
        FIELD_READ,
        FIELD_WRITE,
        METHOD_CALL
    }

    public enum Opcode {
        GETFIELD,
        GETSTATIC,
        INVOKEINTERFACE,
        INVOKESPECIAL,
        INVOKESTATIC,
        INVOKEVIRTUAL,
        PUTFIELD,
        PUTSTATIC
    }

    public ParsedCodeAccess {
        if (kind == null) throw new NullPointerException("kind");
        if (opcode == null) throw new NullPointerException("opcode");
        if (targetOwnerDescriptor == null || targetOwnerDescriptor.isBlank()) {
            throw new IllegalArgumentException("targetOwnerDescriptor must not be blank");
        }
        if (targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("targetName must not be blank");
        }
        if (targetDescriptor == null || targetDescriptor.isBlank()) {
            throw new IllegalArgumentException("targetDescriptor must not be blank");
        }
        if (bytecodeOffset < 0) throw new IllegalArgumentException("bytecodeOffset must not be negative");
    }

    @Override
    public int compareTo(ParsedCodeAccess other) {
        int result = Integer.compare(bytecodeOffset, other.bytecodeOffset);
        if (result != 0) return result;
        result = opcode.compareTo(other.opcode);
        if (result != 0) return result;
        result = targetOwnerDescriptor.compareTo(other.targetOwnerDescriptor);
        if (result != 0) return result;
        result = targetName.compareTo(other.targetName);
        return result != 0 ? result : targetDescriptor.compareTo(other.targetDescriptor);
    }
}
