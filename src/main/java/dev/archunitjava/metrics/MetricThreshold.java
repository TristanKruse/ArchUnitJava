package dev.archunitjava.metrics;

import java.util.Objects;

/** Typed inclusive threshold; construction rejects a limit expressed in the wrong unit. */
public record MetricThreshold(
        MetricName metric, MetricComparison comparison, MetricAmount limit) {
    public MetricThreshold {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(limit, "limit");
        if (metric.unit() != limit.unit()) {
            throw new IllegalArgumentException(metric + " requires unit " + metric.unit());
        }
    }

    public static MetricThreshold atMost(MetricName metric, MetricAmount limit) {
        return new MetricThreshold(metric, MetricComparison.AT_MOST, limit);
    }

    public static MetricThreshold atLeast(MetricName metric, MetricAmount limit) {
        return new MetricThreshold(metric, MetricComparison.AT_LEAST, limit);
    }

    public boolean violatedBy(MetricSample sample) {
        MetricSample value = Objects.requireNonNull(sample, "sample");
        if (value.metric() != metric) {
            throw new IllegalArgumentException("sample does not contain metric " + metric);
        }
        return comparison.violated(value.amount().compareTo(limit));
    }
}
