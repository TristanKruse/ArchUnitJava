package dev.archunitjava.metrics;

import dev.archunitjava.graph.StableId;
import java.util.List;
import java.util.Objects;

/**
 * Robert C. Martin metrics. Couplings count distinct other components, instability is
 * Ce/(Ca+Ce) with 0 for no coupling, abstractness is abstract/total with 0 for no types, and
 * distance is |A+I-1|.
 */
public record ComponentDependencyMetrics(
        StableId component,
        int afferentCoupling,
        int efferentCoupling,
        double instability,
        double abstractness,
        double distanceFromMainSequence) implements Comparable<ComponentDependencyMetrics> {
    public ComponentDependencyMetrics {
        Objects.requireNonNull(component, "component");
        if (afferentCoupling < 0 || efferentCoupling < 0
                || !ratio(instability) || !ratio(abstractness) || !ratio(distanceFromMainSequence)) {
            throw new IllegalArgumentException("invalid component dependency metrics");
        }
    }

    public List<MetricSample> samples() {
        return List.of(
                sample(MetricName.AFFERENT_COUPLING, afferentCoupling),
                sample(MetricName.EFFERENT_COUPLING, efferentCoupling),
                sample(MetricName.INSTABILITY, instability),
                sample(MetricName.ABSTRACTNESS, abstractness),
                sample(MetricName.DISTANCE_FROM_MAIN_SEQUENCE, distanceFromMainSequence));
    }

    @Override
    public int compareTo(ComponentDependencyMetrics other) {
        return component.compareTo(other.component);
    }

    private MetricSample sample(MetricName metric, long value) {
        return new MetricSample(component, metric, MetricAmount.of(value, metric.unit()));
    }

    private MetricSample sample(MetricName metric, double value) {
        return new MetricSample(component, metric, MetricAmount.of(value, metric.unit()));
    }

    private static boolean ratio(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
