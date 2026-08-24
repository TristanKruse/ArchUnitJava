package dev.archunitjava.model;

/** The eight non-void primitive JVM types. */
public enum JvmPrimitiveType implements JvmType {
    BOOLEAN('Z', "boolean"),
    BYTE('B', "byte"),
    CHAR('C', "char"),
    SHORT('S', "short"),
    INT('I', "int"),
    LONG('J', "long"),
    FLOAT('F', "float"),
    DOUBLE('D', "double");

    private final String descriptor;
    private final String displayName;

    JvmPrimitiveType(char descriptor, String displayName) {
        this.descriptor = String.valueOf(descriptor);
        this.displayName = displayName;
    }

    @Override
    public String descriptor() {
        return descriptor;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
