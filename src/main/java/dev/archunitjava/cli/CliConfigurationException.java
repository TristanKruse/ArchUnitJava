package dev.archunitjava.cli;

/** Safe validation failure for declarative CLI input. */
public final class CliConfigurationException extends RuntimeException {
    public CliConfigurationException(String message) {
        super(message);
    }

    public CliConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
