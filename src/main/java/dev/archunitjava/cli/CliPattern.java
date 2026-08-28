package dev.archunitjava.cli;

import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;

/** Bounded exact or glob pattern; arbitrary regular expressions are intentionally unsupported. */
public record CliPattern(Kind kind, String expression) {
    public enum Kind { EXACT, GLOB }

    public CliPattern {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (expression == null || expression.isBlank() || expression.length() > 512) {
            throw new CliConfigurationException(
                    "Pattern expression must contain between 1 and 512 characters");
        }
    }

    public static CliPattern parse(String value) {
        if (value == null) throw new CliConfigurationException("Pattern must not be null");
        int separator = value.indexOf(':');
        if (separator < 1) {
            throw new CliConfigurationException("Pattern must start with exact: or glob:");
        }
        Kind kind = switch (value.substring(0, separator).toLowerCase(java.util.Locale.ROOT)) {
            case "exact" -> Kind.EXACT;
            case "glob" -> Kind.GLOB;
            default -> throw new CliConfigurationException(
                    "Pattern must start with exact: or glob:");
        };
        return new CliPattern(kind, value.substring(separator + 1));
    }

    public JavaPattern toJavaPattern() {
        return switch (kind) {
            case EXACT -> JavaPattern.exact(PatternDomain.QUALIFIED_NAME, expression);
            case GLOB -> JavaPattern.glob(PatternDomain.QUALIFIED_NAME, expression);
        };
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + ':' + expression;
    }
}
