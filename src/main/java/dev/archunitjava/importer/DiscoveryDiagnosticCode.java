package dev.archunitjava.importer;

/** Stable diagnostic categories emitted by project discovery. */
public enum DiscoveryDiagnosticCode {
    INVALID_START,
    NO_PROJECT_FOUND,
    AMBIGUOUS_BUILD_METADATA,
    AMBIGUOUS_PROJECT_ROOTS,
    MALFORMED_METADATA,
    METADATA_TOO_LARGE,
    DYNAMIC_METADATA,
    OUTPUT_OUTSIDE_ROOT
}
