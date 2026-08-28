package dev.archunitjava.integration;

/** Shared CLI operation exposed through build tools. */
public enum BuildCommand {
    CHECK("check"),
    GRAPH("graph");

    private final String cliName;

    BuildCommand(String cliName) {
        this.cliName = cliName;
    }

    String cliName() {
        return cliName;
    }
}
