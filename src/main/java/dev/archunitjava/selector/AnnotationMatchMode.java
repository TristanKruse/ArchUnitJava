package dev.archunitjava.selector;

/** Distinct annotation relationships; no mode silently includes another. */
public enum AnnotationMatchMode {
    DIRECT_DECLARATION,
    META_ANNOTATION,
    INHERITED_DECLARATION,
    TYPE_USE
}
