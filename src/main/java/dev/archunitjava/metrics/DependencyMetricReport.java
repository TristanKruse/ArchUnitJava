package dev.archunitjava.metrics;

import dev.archunitjava.projection.ProjectionDomain;
import java.util.List;
import java.util.Objects;

/** Projection-scoped component and cumulative metrics with threshold-ready samples. */
public record DependencyMetricReport(
        ProjectionDomain domain,
        List<ComponentDependencyMetrics> components,
        CumulativeDependencyMetrics cumulative,
        List<MetricSample> samples) {
    public DependencyMetricReport {
        Objects.requireNonNull(domain, "domain");
        components = Objects.requireNonNull(components, "components")
                .stream().distinct().sorted().toList();
        Objects.requireNonNull(cumulative, "cumulative");
        List<MetricSample> expected = java.util.stream.Stream.concat(
                        components.stream().flatMap(value -> value.samples().stream()),
                        cumulative.samples(domain).stream())
                .sorted().toList();
        samples = Objects.requireNonNull(samples, "samples").stream().distinct().sorted().toList();
        if (!samples.equals(expected)) {
            throw new IllegalArgumentException("dependency samples do not match report values");
        }
    }

    public static DependencyMetricReport of(
            ProjectionDomain domain,
            List<ComponentDependencyMetrics> components,
            CumulativeDependencyMetrics cumulative) {
        List<ComponentDependencyMetrics> stable = components.stream().distinct().sorted().toList();
        List<MetricSample> samples = java.util.stream.Stream.concat(
                        stable.stream().flatMap(value -> value.samples().stream()),
                        cumulative.samples(domain).stream())
                .toList();
        return new DependencyMetricReport(domain, stable, cumulative, samples);
    }
}
