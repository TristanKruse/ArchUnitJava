package dev.archunitjava.model;

/** A local descriptor parse failure with the exact failing offset. */
public final class InvalidJvmDescriptorException extends IllegalArgumentException {
    private final String descriptor;
    private final int offset;
    private final String reason;

    InvalidJvmDescriptorException(String descriptor, int offset, String reason) {
        super("Invalid JVM descriptor at offset " + offset + ": " + reason);
        this.descriptor = descriptor;
        this.offset = offset;
        this.reason = reason;
    }

    public String descriptor() {
        return descriptor;
    }

    public int offset() {
        return offset;
    }

    public String reason() {
        return reason;
    }
}
