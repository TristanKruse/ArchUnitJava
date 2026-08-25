package dev.archunitjava.projection;

import dev.archunitjava.graph.DependencyGraph;
import java.util.List;
import java.util.Objects;

/** Projected graph plus complete accounting for omitted raw edges. */
public record ProjectionResult(
        ProjectionDomain domain,
        DependencyGraph graph,
        List<DroppedProjectionEdge> droppedEdges) {
    public ProjectionResult {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(droppedEdges, "droppedEdges");
        droppedEdges = droppedEdges.stream()
                .map(value -> Objects.requireNonNull(value, "droppedEdge"))
                .sorted()
                .toList();
    }
}
