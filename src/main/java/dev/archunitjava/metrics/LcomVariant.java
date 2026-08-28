package dev.archunitjava.metrics;

/** Supported, explicitly distinct lack-of-cohesion formulas. */
public enum LcomVariant {
    /** max(method pairs sharing no instance field - pairs sharing a field, 0). */
    CK_LCOM1(MetricName.CK_LCOM1),
    /** Connected components of instance methods joined by shared fields or direct self calls. */
    LCOM4(MetricName.LCOM4),
    /** min(1, max(0, (M - average methods per field) / (M - 1))). */
    HENDERSON_SELLERS(MetricName.HENDERSON_SELLERS_LCOM);

    private final MetricName metric;

    LcomVariant(MetricName metric) {
        this.metric = metric;
    }

    public MetricName metric() {
        return metric;
    }
}
