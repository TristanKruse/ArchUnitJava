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
    private static final int MAX_SAFE_REGEX_CHARACTERS = 512;
    private static final int MAX_SAFE_REGEX_CANDIDATE_CHARACTERS = 4096;
    private static final int MAX_SAFE_REPETITION = 1024;

    private final PatternDescription description;
    private final String exact;
    private final Pattern compiled;
    private final boolean boundedRegex;

    private JavaPattern(
            PatternDescription description, String exact, Pattern compiled, boolean boundedRegex) {
        this.description = description;
        this.exact = exact;
        this.compiled = compiled;
        this.boundedRegex = boundedRegex;
    }

    public static JavaPattern exact(PatternDomain domain, String expression) {
        PatternDescription description = description(PatternSyntax.EXACT, domain, expression);
        return new JavaPattern(description, expression, null, false);
    }

    public static JavaPattern glob(PatternDomain domain, String expression) {
        PatternDescription description = description(PatternSyntax.GLOB, domain, expression);
        return new JavaPattern(description, null, compileGlob(domain, expression), false);
    }

    public static JavaPattern regex(PatternDomain domain, String expression) {
        PatternDescription description = description(PatternSyntax.REGEX, domain, expression);
        validateSafeRegex(expression);
        return compiled(description, expression, true);
    }

    /**
     * Compiles an unrestricted JDK regular expression for trusted in-process policy only.
     *
     * <p>Unlike {@link #regex(PatternDomain, String)}, this method permits constructs with
     * unbounded backtracking. Never pass tenant-, repository-, or network-controlled text here.
     */
    public static JavaPattern trustedRegex(PatternDomain domain, String expression) {
        PatternDescription description = description(PatternSyntax.TRUSTED_REGEX, domain, expression);
        return compiled(description, expression, false);
    }

    private static JavaPattern compiled(
            PatternDescription description, String expression, boolean boundedRegex) {
        try {
            return new JavaPattern(description, null, Pattern.compile(expression), boundedRegex);
        } catch (PatternSyntaxException error) {
            throw new InvalidPatternException(
                    "Invalid regex pattern '" + expression + "': " + error.getDescription(), error);
        }
    }

    public boolean matches(String candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (boundedRegex && candidate.length() > MAX_SAFE_REGEX_CANDIDATE_CHARACTERS) {
            throw new PatternEvaluationException(
                    "Safe regex candidate exceeds " + MAX_SAFE_REGEX_CANDIDATE_CHARACTERS
                            + " UTF-16 code units");
        }
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
        if (domain == null || expression == null) {
            throw new InvalidPatternException("Invalid " + syntax.name().toLowerCase() + " pattern");
        }
        try {
            return new PatternDescription(syntax, domain, expression);
        } catch (IllegalArgumentException error) {
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

    private static void validateSafeRegex(String expression) {
        if (expression.length() > MAX_SAFE_REGEX_CHARACTERS) {
            throw new InvalidPatternException(
                    "Safe regex exceeds " + MAX_SAFE_REGEX_CHARACTERS + " UTF-16 code units");
        }
        boolean escaped = false;
        boolean characterClass = false;
        int variableQuantifiers = 0;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (escaped) {
                if (!characterClass && (Character.isDigit(character) || character == 'k')) {
                    throw unsafeRegex("backreferences are not supported");
                }
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (characterClass) {
                if (character == ']') characterClass = false;
                continue;
            }
            if (character == '[') {
                characterClass = true;
                continue;
            }
            if (character == '(' || character == ')' || character == '|') {
                throw unsafeRegex("groups, lookarounds, and alternation are not supported");
            }
            if (character == '*' || character == '+' || character == '?') {
                variableQuantifiers = requireSingleVariableQuantifier(variableQuantifiers);
                continue;
            }
            if (character == '{') {
                int close = expression.indexOf('}', index + 1);
                if (close < 0) continue;
                String bounds = expression.substring(index + 1, close);
                int comma = bounds.indexOf(',');
                String minimum = comma < 0 ? bounds : bounds.substring(0, comma);
                String maximum = comma < 0 ? bounds : bounds.substring(comma + 1);
                if (!decimal(minimum) || (!maximum.isEmpty() && !decimal(maximum))) continue;
                int minimumValue = Integer.parseInt(minimum);
                int maximumValue = maximum.isEmpty() ? -1 : Integer.parseInt(maximum);
                if (minimumValue > MAX_SAFE_REPETITION
                        || maximumValue > MAX_SAFE_REPETITION) {
                    throw unsafeRegex("bounded repetition exceeds " + MAX_SAFE_REPETITION);
                }
                if (comma >= 0 && maximumValue != minimumValue) {
                    variableQuantifiers = requireSingleVariableQuantifier(variableQuantifiers);
                }
                index = close;
            }
        }
    }

    private static int requireSingleVariableQuantifier(int count) {
        if (count >= 1) {
            throw unsafeRegex("at most one variable quantifier is supported");
        }
        return count + 1;
    }

    private static boolean decimal(String value) {
        if (value.isEmpty() || value.length() > 9) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    private static InvalidPatternException unsafeRegex(String reason) {
        return new InvalidPatternException(
                "Regex is outside the safe subset: " + reason
                        + "; use exact/glob or trustedRegex for trusted policy");
    }
}
