package dev.archunitjava.metrics;

import dev.archunitjava.graph.StableId;
import dev.archunitjava.projection.ProjectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Lakos metrics. dependsOn includes the subject itself; CCD is their sum, ACD=CCD/N,
 * RACD=ACD/N, and NCCD=CCD divided by a same-size complete-level balanced binary tree's CCD.
 * Every value is zero for an empty projection.
 */
public record CumulativeDependencyMetrics(
        int componentCount,
        long cumulativeComponentDependency,
        double averageComponentDependency,
        double relativeAverageComponentDependency,
        double normalizedCumulativeComponentDependency,
        Map<StableId, Integer> dependsOn) {
    public CumulativeDependencyMetrics {
        if (componentCount < 0 || cumulativeComponentDependency < 0
                || !nonNegative(averageComponentDependency)
                || !nonNegative(relativeAverageComponentDependency)
                || !nonNegative(normalizedCumulativeComponentDependency)) {
            throw new IllegalArgumentException("invalid cumulative dependency metrics");
        }
        TreeMap<StableId, Integer> stable = new TreeMap<>();
        Objects.requireNonNull(dependsOn, "dependsOn").forEach((subject, count) -> {
            Objects.requireNonNull(subject, "subject");
            if (count == null || count < 1 || count > componentCount) {
                throw new IllegalArgumentException("invalid dependsOn count");
            }
            stable.put(subject, count);
        });
        dependsOn = java.util.Collections.unmodifiableMap(stable);
        if (dependsOn.size() != componentCount
                || dependsOn.values().stream().mapToLong(Integer::longValue).sum()
                        != cumulativeComponentDependency) {
            throw new IllegalArgumentException("dependsOn values do not reproduce CCD");
        }
    }

    public List<MetricSample> samples(ProjectionDomain domain) {
        MetricSystemId subject = new MetricSystemId(domain);
        return List.of(
                sample(subject, MetricName.CUMULATIVE_COMPONENT_DEPENDENCY,
                        cumulativeComponentDependency),
                sample(subject, MetricName.AVERAGE_COMPONENT_DEPENDENCY,
                        averageComponentDependency),
                sample(subject, MetricName.RELATIVE_AVERAGE_COMPONENT_DEPENDENCY,
                        relativeAverageComponentDependency),
                sample(subject, MetricName.NORMALIZED_CUMULATIVE_COMPONENT_DEPENDENCY,
                        normalizedCumulativeComponentDependency));
    }

    private static MetricSample sample(StableId subject, MetricName metric, long value) {
        return new MetricSample(subject, metric, MetricAmount.of(value, metric.unit()));
    }

    private static MetricSample sample(StableId subject, MetricName metric, double value) {
        return new MetricSample(subject, metric, MetricAmount.of(value, metric.unit()));
    }

    private static boolean nonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
