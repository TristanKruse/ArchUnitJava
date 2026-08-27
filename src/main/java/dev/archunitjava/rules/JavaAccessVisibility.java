package dev.archunitjava.rules;

/** Java/JVM access classification kept separate from JPMS package exports. */
public enum JavaAccessVisibility {
    PUBLIC,
    PROTECTED_SAME_PACKAGE,
    PROTECTED_CROSS_PACKAGE,
    PACKAGE_PRIVATE,
    PACKAGE_PRIVATE_INACCESSIBLE,
    PRIVATE_NESTMATE,
    PRIVATE_INACCESSIBLE
}
