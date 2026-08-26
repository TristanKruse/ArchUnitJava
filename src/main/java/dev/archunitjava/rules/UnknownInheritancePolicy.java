package dev.archunitjava.rules;

/** Policy for a transitive conclusion whose external inheritance is not fully known. */
public enum UnknownInheritancePolicy {
    FAIL,
    IGNORE
}
