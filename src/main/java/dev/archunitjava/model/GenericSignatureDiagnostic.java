package dev.archunitjava.model;

/** Non-fatal malformed generic metadata retained alongside its erased fallback. */
public record GenericSignatureDiagnostic(String signature, int errorOffset, String reason)
        implements Comparable<GenericSignatureDiagnostic> {
    public GenericSignatureDiagnostic {
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("signature must not be blank");
        }
        if (errorOffset < 0 || errorOffset > signature.length()) {
            throw new IllegalArgumentException("errorOffset is outside the signature");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    static GenericSignatureDiagnostic from(InvalidGenericSignatureException failure) {
        return new GenericSignatureDiagnostic(
                failure.signature(), failure.errorOffset(), failure.reason());
    }

    @Override
    public int compareTo(GenericSignatureDiagnostic other) {
        int result = signature.compareTo(other.signature);
        if (result != 0) return result;
        result = Integer.compare(errorOffset, other.errorOffset);
        return result != 0 ? result : reason.compareTo(other.reason);
    }
}
