package dev.archunitjava.baseline;

/** Strict baseline input that is malformed, unsupported, or outside configured limits. */
public final class BaselineFormatException extends IllegalArgumentException {
    public BaselineFormatException(String message) {
        super(message);
    }

    public BaselineFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
