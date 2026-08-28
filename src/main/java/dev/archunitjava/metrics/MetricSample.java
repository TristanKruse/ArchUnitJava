package dev.archunitjava.metrics;

import dev.archunitjava.graph.StableId;
import java.util.Objects;

/** One deterministic metric observation for one typed architecture subject. */
public record MetricSample(StableId subject, MetricName metric, MetricAmount amount)
        implements Comparable<MetricSample> {
    public MetricSample {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(amount, "amount");
        if (metric.unit() != amount.unit()) {
            throw new IllegalArgumentException(metric + " requires unit " + metric.unit());
        }
    }

    @Override
    public int compareTo(MetricSample other) {
        int result = metric.compareTo(other.metric);
        if (result != 0) return result;
        result = subject.compareTo(other.subject);
        return result != 0 ? result : amount.compareTo(other.amount);
    }
}
