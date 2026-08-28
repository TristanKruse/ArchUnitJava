package dev.archunitjava.cli;

/** Supported graph report encodings. */
public enum CliGraphFormat {
    DOT,
    MERMAID,
    JSON,
    CSV,
    D2,
    HTML;

    static CliGraphFormat parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new CliConfigurationException("Unsupported graph format: " + value, error);
        }
    }
}
