package dev.archunitjava.importer;

/** Machine-readable failures and exclusions produced while enumerating inputs. */
public enum InputDiagnosticCode {
    DUPLICATE_INPUT,
    DUPLICATE_RESOURCE,
    INVALID_RESOURCE_NAME,
    IO_FAILURE,
    MISSING_INPUT,
    RESOURCE_LIMIT_EXCEEDED,
    SYMLINK_SKIPPED,
    UNREADABLE_INPUT,
    UNSUPPORTED_INPUT
}
