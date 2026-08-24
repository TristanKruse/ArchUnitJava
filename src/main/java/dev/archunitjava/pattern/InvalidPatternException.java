package dev.archunitjava.pattern;

/** A user-supplied pattern that cannot be compiled under its declared syntax. */
public final class InvalidPatternException extends IllegalArgumentException {
    public InvalidPatternException(String message) {
        super(message);
    }

    public InvalidPatternException(String message, Throwable cause) {
        super(message, cause);
    }
}
