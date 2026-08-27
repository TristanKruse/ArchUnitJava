package dev.archunitjava.slices;

/** Stable resolution when one type is offered to multiple differently named slices. */
public enum SliceOverlapPolicy {
    FAIL,
    FIRST_BY_NAME
}
