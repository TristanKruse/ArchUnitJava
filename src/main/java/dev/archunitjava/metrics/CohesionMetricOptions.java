package dev.archunitjava.metrics;

/** Cohesion policy; static members are always outside class-instance cohesion. */
public record CohesionMetricOptions(boolean includeSyntheticMembers) {
    public static CohesionMetricOptions defaults() {
        return new CohesionMetricOptions(false);
    }
}
