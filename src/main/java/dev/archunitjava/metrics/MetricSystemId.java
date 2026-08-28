package dev.archunitjava.metrics;

import dev.archunitjava.graph.StableId;
import dev.archunitjava.projection.ProjectionDomain;
import java.util.Objects;

/** Threshold subject representing one complete filtered projection. */
public record MetricSystemId(ProjectionDomain domain) implements StableId {
    public MetricSystemId {
        Objects.requireNonNull(domain, "domain");
    }

    @Override
    public String stableKey() {
        return "metric-system:" + domain.name().toLowerCase(java.util.Locale.ROOT);
    }
}
