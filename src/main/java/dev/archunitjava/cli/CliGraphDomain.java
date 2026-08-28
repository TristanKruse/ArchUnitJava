package dev.archunitjava.cli;

/** Built-in graph projections available from declarative configuration. */
public enum CliGraphDomain {
    TYPES,
    PACKAGES;

    static CliGraphDomain parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new CliConfigurationException("Unsupported graph domain: " + value, error);
        }
    }
}
