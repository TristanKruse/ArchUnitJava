package dev.archunitjava.pattern;

/** The semantic text domain in which a pattern is evaluated. */
public enum PatternDomain {
    /** Forward-slash-separated class-path resource names. */
    RESOURCE_PATH('/'),

    /** Dot-separated package, binary-type, or source-style type names. */
    QUALIFIED_NAME('.');

    private final char separator;

    PatternDomain(char separator) {
        this.separator = separator;
    }

    /** Returns the separator that a single glob star cannot cross. */
    public char separator() {
        return separator;
    }
}
