package dev.archunitjava.metrics;

import dev.archunitjava.graph.TypeId;
import java.util.List;
import java.util.Objects;

/** Aggregate counts plus threshold-ready subject samples and explicit omissions. */
public record SourceMetricReport(
        SourceCounts aggregate,
        List<MetricSample> samples,
        List<TypeId> missingSourceTypes,
        List<TypeId> excludedGeneratedTypes) {
    public SourceMetricReport {
        Objects.requireNonNull(aggregate, "aggregate");
        samples = Objects.requireNonNull(samples, "samples").stream().distinct().sorted().toList();
        missingSourceTypes = Objects.requireNonNull(missingSourceTypes, "missingSourceTypes")
                .stream().distinct().sorted().toList();
        excludedGeneratedTypes = Objects.requireNonNull(
                excludedGeneratedTypes, "excludedGeneratedTypes")
                .stream().distinct().sorted().toList();
    }
}
