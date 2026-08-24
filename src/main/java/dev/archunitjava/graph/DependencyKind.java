package dev.archunitjava.graph;

/** The bytecode or declaration relationship that caused a dependency. */
public enum DependencyKind {
    ANNOTATION,
    CLASS_LITERAL,
    CONSTRUCTOR_CALL,
    EXTENDS,
    FIELD_ACCESS,
    FIELD_TYPE,
    GENERIC_SIGNATURE,
    IMPLEMENTS,
    INSTANCEOF,
    METHOD_CALL,
    METHOD_PARAMETER_TYPE,
    METHOD_RETURN_TYPE,
    THROWS,
    TYPE_REFERENCE
}
