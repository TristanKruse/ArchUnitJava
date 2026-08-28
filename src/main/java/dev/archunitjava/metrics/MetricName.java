package dev.archunitjava.metrics;

import java.util.Objects;

/** Stable metric identifiers with one mandatory unit each. */
public enum MetricName {
    PACKAGE_TYPE_COUNT(MetricUnit.TYPES),
    TYPE_MEMBER_COUNT(MetricUnit.MEMBERS),
    TYPE_FIELD_COUNT(MetricUnit.FIELDS),
    TYPE_METHOD_COUNT(MetricUnit.METHODS),
    TYPE_CONSTRUCTOR_COUNT(MetricUnit.CONSTRUCTORS),
    TYPE_STATIC_INITIALIZER_COUNT(MetricUnit.STATIC_INITIALIZERS),
    TYPE_RECORD_COMPONENT_COUNT(MetricUnit.RECORD_COMPONENTS),
    SOURCE_PHYSICAL_LINES(MetricUnit.LINES),
    SOURCE_BLANK_LINES(MetricUnit.LINES),
    SOURCE_COMMENT_LINES(MetricUnit.LINES),
    SOURCE_CODE_LINES(MetricUnit.LINES),
    CK_LCOM1(MetricUnit.METHOD_PAIRS),
    LCOM4(MetricUnit.COHESION_COMPONENTS),
    HENDERSON_SELLERS_LCOM(MetricUnit.RATIO),
    AFFERENT_COUPLING(MetricUnit.COMPONENTS),
    EFFERENT_COUPLING(MetricUnit.COMPONENTS),
    INSTABILITY(MetricUnit.RATIO),
    ABSTRACTNESS(MetricUnit.RATIO),
    DISTANCE_FROM_MAIN_SEQUENCE(MetricUnit.RATIO),
    CUMULATIVE_COMPONENT_DEPENDENCY(MetricUnit.DEPENDENCIES),
    AVERAGE_COMPONENT_DEPENDENCY(MetricUnit.COMPONENTS),
    RELATIVE_AVERAGE_COMPONENT_DEPENDENCY(MetricUnit.RATIO),
    NORMALIZED_CUMULATIVE_COMPONENT_DEPENDENCY(MetricUnit.RATIO);

    private final MetricUnit unit;

    MetricName(MetricUnit unit) {
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    public MetricUnit unit() {
        return unit;
    }
}
