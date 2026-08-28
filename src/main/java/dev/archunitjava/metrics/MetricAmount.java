package dev.archunitjava.metrics;

import java.math.BigDecimal;
import java.util.Objects;

/** Exact decimal metric amount paired with its non-interchangeable unit. */
public record MetricAmount(BigDecimal value, MetricUnit unit) implements Comparable<MetricAmount> {
    public MetricAmount {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        if (value.signum() < 0) throw new IllegalArgumentException("metric value must not be negative");
        value = normalized(value);
    }

    public static MetricAmount of(long value, MetricUnit unit) {
        return new MetricAmount(BigDecimal.valueOf(value), unit);
    }

    public static MetricAmount of(double value, MetricUnit unit) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("metric value must be finite");
        return new MetricAmount(BigDecimal.valueOf(value), unit);
    }

    public String stableValue() {
        return value.toPlainString();
    }

    @Override
    public int compareTo(MetricAmount other) {
        if (unit != Objects.requireNonNull(other, "other").unit) {
            throw new IllegalArgumentException("cannot compare " + unit + " with " + other.unit);
        }
        return value.compareTo(other.value);
    }

    private static BigDecimal normalized(BigDecimal value) {
        BigDecimal result = value.stripTrailingZeros();
        return result.scale() < 0 ? result.setScale(0) : result;
    }
}
