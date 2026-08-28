package dev.archunitjava.metrics;

import java.util.List;
import java.util.Objects;

/** Formula-specific cohesion results plus only the threshold-safe computed samples. */
public record CohesionMetricReport(List<CohesionValue> values, List<MetricSample> samples) {
    public CohesionMetricReport {
        values = Objects.requireNonNull(values, "values").stream().distinct().sorted().toList();
        samples = Objects.requireNonNull(samples, "samples").stream().distinct().sorted().toList();
        List<MetricSample> expected = values.stream()
                .map(CohesionValue::sample).flatMap(java.util.Optional::stream).sorted().toList();
        if (!samples.equals(expected)) {
            throw new IllegalArgumentException("cohesion samples do not match computed values");
        }
    }

    public static CohesionMetricReport of(List<CohesionValue> values) {
        List<CohesionValue> stable = Objects.requireNonNull(values, "values")
                .stream().distinct().sorted().toList();
        return new CohesionMetricReport(stable, stable.stream()
                .map(CohesionValue::sample).flatMap(java.util.Optional::stream).toList());
    }
}
