package dev.archunitjava.integration;

/** Clear configuration or lifecycle failure reported to the hosting build tool. */
public final class BuildIntegrationException extends RuntimeException {
    public BuildIntegrationException(String message) {
        super(message);
    }

    public BuildIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
