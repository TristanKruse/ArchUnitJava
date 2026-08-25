package dev.archunitjava.projection;

import dev.archunitjava.graph.DependencyEdge;
import java.util.Objects;

/** One omitted raw edge and its deterministic reason. */
public record DroppedProjectionEdge(DependencyEdge edge, ProjectionDropReason reason)
        implements Comparable<DroppedProjectionEdge> {
    public DroppedProjectionEdge {
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public int compareTo(DroppedProjectionEdge other) {
        int result = edge.compareTo(other.edge);
        return result != 0 ? result : reason.compareTo(other.reason);
    }
}
