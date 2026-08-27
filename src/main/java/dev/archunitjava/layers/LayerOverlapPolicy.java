package dev.archunitjava.layers;

/** Resolution of a type selected by multiple differently named layers. */
public enum LayerOverlapPolicy {
    FAIL,
    FIRST_BY_NAME
}
