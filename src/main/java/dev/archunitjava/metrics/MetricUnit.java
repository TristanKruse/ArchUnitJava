package dev.archunitjava.metrics;

/** Units carried by metric values and limits so unlike quantities cannot be compared. */
public enum MetricUnit {
    PACKAGES,
    TYPES,
    MEMBERS,
    FIELDS,
    METHODS,
    CONSTRUCTORS,
    STATIC_INITIALIZERS,
    RECORD_COMPONENTS,
    SOURCE_FILES,
    LINES,
    METHOD_PAIRS,
    COHESION_COMPONENTS,
    COMPONENTS,
    DEPENDENCIES,
    RATIO
}
