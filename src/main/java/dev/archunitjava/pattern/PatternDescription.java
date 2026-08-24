package dev.archunitjava.pattern;

import java.util.Objects;

/** Immutable, deterministic metadata describing a compiled matcher. */
public record PatternDescription(PatternSyntax syntax, PatternDomain domain, String expression)
        implements Comparable<PatternDescription> {
    public PatternDescription {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(domain, "domain");
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Pattern expression must not be blank");
        }
    }

    @Override
    public int compareTo(PatternDescription other) {
        int result = domain.compareTo(other.domain);
        if (result != 0) return result;
        result = syntax.compareTo(other.syntax);
        return result != 0 ? result : expression.compareTo(other.expression);
    }
}
