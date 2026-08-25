package dev.archunitjava.projection;

/** Why a raw edge did not appear in a projected graph. */
public enum ProjectionDropReason {
    EXPLICITLY_EXCLUDED,
    FILTERED_KIND,
    SELF_EDGE,
    UNMAPPED_ENDPOINT
}
