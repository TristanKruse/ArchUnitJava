package dev.archunitjava.pattern;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * An immutable pattern compiled once for repeated matching.
 *
 * <p>Matching is always against the complete candidate. In glob syntax, {@code *} matches zero or
 * more characters except the domain separator, {@code ?} matches one non-separator character, and
 * {@code **} may cross separators. A backslash quotes the following glob character.
 */
public final class JavaPattern {
    private final PatternDescription description;
    private final String exact;
    private final Pattern compiled;

    private JavaPattern(PatternDescription description, String exact, Pattern compiled) {
        this.description = description;
        this.exact = exact;
        this.compiled = compiled;
    }

    public static JavaPattern exact(PatternDomain domain, String expression) {
        PatternDescription description = description(PatternSyntax.EXACT, domain, expression);
        return new JavaPattern(description, expression, null);
    }

    public static JavaPattern glob(PatternDomain domain, String expression) {
        PatternDescription description = description(PatternSyntax.GLOB, domain, expression);
        return new JavaPattern(description, null, compileGlob(domain, expression));
    }

    public static JavaPattern regex(PatternDomain domain, String expression) {
        PatternDescription description = description(PatternSyntax.REGEX, domain, expression);
        try {
            return new JavaPattern(description, null, Pattern.compile(expression));
        } catch (PatternSyntaxException error) {
            throw new InvalidPatternException(
                    "Invalid regex pattern '" + expression + "': " + error.getDescription(), error);
        }
    }

    public boolean matches(String candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return exact != null ? exact.equals(candidate) : compiled.matcher(candidate).matches();
    }

    public PatternDescription description() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JavaPattern pattern && description.equals(pattern.description);
    }

    @Override
    public int hashCode() {
        return description.hashCode();
    }

    @Override
    public String toString() {
        return description.toString();
    }

    private static PatternDescription description(
            PatternSyntax syntax, PatternDomain domain, String expression) {
        try {
            return new PatternDescription(syntax, domain, expression);
        } catch (NullPointerException | IllegalArgumentException error) {
            throw new InvalidPatternException("Invalid " + syntax.name().toLowerCase() + " pattern", error);
        }
    }

    private static Pattern compileGlob(PatternDomain domain, String expression) {
        String separator = domain == PatternDomain.RESOURCE_PATH ? "/" : ".";
        StringBuilder regex = new StringBuilder("\\A");
        for (int offset = 0; offset < expression.length();) {
            int codePoint = expression.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\\') {
                if (offset == expression.length()) {
                    throw new InvalidPatternException("Glob pattern ends with an escape character");
                }
                int escaped = expression.codePointAt(offset);
                offset += Character.charCount(escaped);
                regex.append(Pattern.quote(new String(Character.toChars(escaped))));
            } else if (codePoint == '*') {
                if (offset < expression.length() && expression.codePointAt(offset) == '*') {
                    offset += Character.charCount('*');
                    regex.append("[\\s\\S]*");
                } else {
                    regex.append("[^").append(separator).append("]*");
                }
            } else if (codePoint == '?') {
                regex.append("[^").append(separator).append(']');
            } else {
                regex.append(Pattern.quote(new String(Character.toChars(codePoint))));
            }
        }
        return Pattern.compile(regex.append("\\z").toString());
    }
}
