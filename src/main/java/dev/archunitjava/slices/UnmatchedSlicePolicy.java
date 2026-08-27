package dev.archunitjava.slices;

/** Treatment of imported types that no capture pattern or explicit selector assigns. */
public enum UnmatchedSlicePolicy {
    IGNORE,
    FAIL
}
