package dev.archunitjava.cli;

/** Stable process outcomes shared by all CLI entry points and build integrations. */
public enum CliExitCode {
    SUCCESS(0, "command completed successfully"),
    USAGE(2, "invalid command-line usage"),
    INVALID_CONFIGURATION(3, "configuration is invalid or outside approved roots"),
    ANALYSIS_ERROR(4, "analysis or rendering could not complete reliably"),
    POLICY_VIOLATION(5, "one or more architecture policies failed");

    private final int code;
    private final String description;

    CliExitCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static String documentation() {
        StringBuilder out = new StringBuilder("Exit codes:\n");
        for (CliExitCode value : values()) {
            out.append("  ").append(value.code).append("  ")
                    .append(value.description).append('\n');
        }
        return out.toString();
    }
}
