package dev.archunitjava.model;

/** Selects which class-file evidence contributes referenced types to a later rule. */
public enum TypeReferenceEvidence {
    ERASED,
    GENERIC,
    COMBINED
}
