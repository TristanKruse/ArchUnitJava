package dev.archunitjava.model;

/** Exact JVM opcode; no runtime dispatch target is inferred from it. */
public enum JavaCodeAccessOpcode {
    GETFIELD,
    GETSTATIC,
    INVOKEINTERFACE,
    INVOKESPECIAL,
    INVOKESTATIC,
    INVOKEVIRTUAL,
    PUTFIELD,
    PUTSTATIC
}
