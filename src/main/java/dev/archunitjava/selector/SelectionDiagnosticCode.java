package dev.archunitjava.selector;

/** Reasons a selector could not evaluate a Java semantic name completely. */
public enum SelectionDiagnosticCode {
    CYCLIC_LEXICAL_OWNERSHIP,
    MISSING_LEXICAL_OWNER,
    UNKNOWN_NESTING_EVIDENCE
}
