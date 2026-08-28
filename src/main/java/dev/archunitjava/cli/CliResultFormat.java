package dev.archunitjava.cli;

/** Supported result report encodings. */
public enum CliResultFormat {
    CONSOLE,
    JSON,
    SARIF,
    JUNIT_XML;

    static CliResultFormat parse(String value) {
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "console" -> CONSOLE;
            case "json" -> JSON;
            case "sarif" -> SARIF;
            case "junit", "junit-xml" -> JUNIT_XML;
            default -> throw new CliConfigurationException("Unsupported result format: " + value);
        };
    }
}
