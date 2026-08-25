package dev.archunitjava.model;

/** Distinct declaration and type-use locations for imported annotations. */
public enum AnnotationSiteKind {
    TYPE_DECLARATION,
    FIELD_DECLARATION,
    METHOD_DECLARATION,
    CONSTRUCTOR_DECLARATION,
    PACKAGE_DECLARATION,
    PARAMETER,
    RECORD_COMPONENT,
    TYPE_USE
}
