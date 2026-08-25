package dev.archunitjava.importer;

/** Machine-readable failures produced while reading untrusted class files. */
public enum ClassFileDiagnosticCode {
    DIAGNOSTIC_LIMIT_REACHED,
    IO_FAILURE,
    MALFORMED_CLASS_FILE,
    RESOURCE_TOO_LARGE,
    UNSUPPORTED_CLASS_VERSION
}
