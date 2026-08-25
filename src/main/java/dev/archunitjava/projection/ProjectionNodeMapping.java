package dev.archunitjava.projection;

import dev.archunitjava.graph.StableId;
import java.util.Objects;

/** Explicit source-to-projected identity mapping used by classpath and module views. */
public record ProjectionNodeMapping(StableId source, StableId target)
        implements Comparable<ProjectionNodeMapping> {
    public ProjectionNodeMapping {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public int compareTo(ProjectionNodeMapping other) {
        int result = source.compareTo(other.source);
        return result != 0 ? result : target.compareTo(other.target);
    }
}
