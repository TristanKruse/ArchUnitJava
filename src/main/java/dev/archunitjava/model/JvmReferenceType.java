package dev.archunitjava.model;

/** A reference type identified canonically by its Java binary name. */
public record JvmReferenceType(String binaryName) implements JvmType {
    public JvmReferenceType {
        if (binaryName == null
                || binaryName.isBlank()
                || binaryName.startsWith(".")
                || binaryName.endsWith(".")
                || binaryName.contains("..")
                || binaryName.indexOf('/') >= 0
                || binaryName.indexOf('[') >= 0
                || binaryName.indexOf(';') >= 0
                || binaryName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid JVM reference binary name: " + binaryName);
        }
    }

    @Override
    public String descriptor() {
        return "L" + binaryName.replace('.', '/') + ";";
    }

    @Override
    public String displayName() {
        return binaryName;
    }
}
