package dev.archunitjava.importer;

/** Machine-readable failures produced while reading untrusted class files. */
public enum ClassFileDiagnosticCode {
    IO_FAILURE,
    MALFORMED_CLASS_FILE,
    RESOURCE_TOO_LARGE,
    UNSUPPORTED_CLASS_VERSION
}
