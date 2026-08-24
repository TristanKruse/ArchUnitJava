package dev.archunitjava.model;

/** Direct JVM hierarchy relationship without conflating interface roles. */
public enum HierarchyRelationshipKind {
    EXTENDS_CLASS,
    EXTENDS_INTERFACE,
    IMPLEMENTS_INTERFACE
}
