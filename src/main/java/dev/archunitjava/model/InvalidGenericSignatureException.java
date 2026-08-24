package dev.archunitjava.model;

/** Reports a deterministic location and reason for malformed JVM generic metadata. */
public final class InvalidGenericSignatureException extends IllegalArgumentException {
    private final String signature;
    private final int errorOffset;
    private final String reason;

    InvalidGenericSignatureException(String signature, int errorOffset, String reason) {
        super(reason + " at offset " + errorOffset + " in generic signature '" + signature + "'");
        this.signature = signature;
        this.errorOffset = errorOffset;
        this.reason = reason;
    }

    public String signature() {
        return signature;
    }

    public int errorOffset() {
        return errorOffset;
    }

    public String reason() {
        return reason;
    }
}
