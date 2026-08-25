package dev.archunitjava.model;

/** Whether a dependency comes from erased, generic-only, annotation, or relationship evidence. */
public enum DeclarationDependencyEvidenceKind {
    ANNOTATION,
    DECLARED_RELATIONSHIP,
    ERASED,
    GENERIC_ONLY
}
