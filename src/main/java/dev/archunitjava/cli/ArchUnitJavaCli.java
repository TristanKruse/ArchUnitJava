package dev.archunitjava.cli;

/** Process entry point; all testable behavior lives in {@link CliRunner}. */
public final class ArchUnitJavaCli {
    private ArchUnitJavaCli() {}

    public static void main(String[] arguments) {
        int exitCode = new CliRunner().run(arguments, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }
}
