package dev.archunitjava.projection;

import java.util.List;
import java.util.Objects;

/** Pure cycle-analysis output, including whether diagnostic enumeration hit a bound. */
public record CycleAnalysisResult(
        List<StronglyConnectedComponent> components,
        List<ElementaryCycle> cycles,
        boolean enumerationPerformed,
        boolean enumerationTruncated,
        long traversedEdges) {
    public CycleAnalysisResult {
        Objects.requireNonNull(components, "components");
        components = components.stream()
                .map(value -> Objects.requireNonNull(value, "component"))
                .distinct()
                .sorted()
                .toList();
        Objects.requireNonNull(cycles, "cycles");
        cycles = cycles.stream()
                .map(value -> Objects.requireNonNull(value, "cycle"))
                .distinct()
                .sorted()
                .toList();
        if (!enumerationPerformed && (!cycles.isEmpty() || enumerationTruncated || traversedEdges != 0)) {
            throw new IllegalArgumentException("Disabled enumeration cannot contain search results");
        }
        if (traversedEdges < 0) throw new IllegalArgumentException("traversedEdges must not be negative");
    }

    public List<StronglyConnectedComponent> cyclicComponents() {
        return components.stream().filter(StronglyConnectedComponent::cyclic).toList();
    }
}
