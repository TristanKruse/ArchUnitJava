package dev.archunitjava.pattern;

/** A pattern evaluation that exceeds a documented safety boundary. */
public final class PatternEvaluationException extends IllegalArgumentException {
    public PatternEvaluationException(String message) {
        super(message);
    }
}
