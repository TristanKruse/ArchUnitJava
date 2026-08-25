package dev.archunitjava.importer;

/** Caller policy for packaging failures that may otherwise be collected as diagnostics. */
public record ImportFailurePolicy(
        boolean failOnUnsupportedClassVersion, boolean failOnDamagedArchive) {
    public static ImportFailurePolicy collectDiagnostics() {
        return new ImportFailurePolicy(false, false);
    }

    public static ImportFailurePolicy strict() {
        return new ImportFailurePolicy(true, true);
    }
}
