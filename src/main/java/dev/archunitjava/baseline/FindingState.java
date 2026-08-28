package dev.archunitjava.baseline;

/** Adoption state assigned by comparing current findings to a reviewed baseline. */
public enum FindingState {
    NEW,
    UNCHANGED,
    MOVED,
    RESOLVED,
    SUPPRESSED,
    EXPIRED
}
