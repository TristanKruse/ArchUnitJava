package dev.archunitjava.model;

/** The JVM void return type, which is not legal as a field or parameter type. */
public enum JvmVoidType implements JvmType {
    VOID;

    @Override
    public String descriptor() {
        return "V";
    }

    @Override
    public String displayName() {
        return "void";
    }
}
