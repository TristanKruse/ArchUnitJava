package dev.archunitjava.selector;

/** Reasons a selector could not evaluate a Java semantic name completely. */
public enum SelectionDiagnosticCode {
    CYCLIC_LEXICAL_OWNERSHIP,
    INCOMPLETE_INHERITED_ANNOTATION,
    INCOMPLETE_META_ANNOTATION,
    MISSING_LEXICAL_OWNER,
    UNKNOWN_ASSIGNABILITY,
    UNKNOWN_NESTING_EVIDENCE
}
