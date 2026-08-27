package dev.archunitjava.rules;

/** Explicit treatment of accesses originating in synthetic types, methods, or bridges. */
public enum CompilerAccessPolicy {
    IGNORE,
    INCLUDE
}
