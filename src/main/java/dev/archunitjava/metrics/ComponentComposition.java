package dev.archunitjava.metrics;

import dev.archunitjava.graph.StableId;
import java.util.Objects;

/** Type inventory used only for abstractness; dependency counts always come from the projection. */
public record ComponentComposition(StableId component, int typeCount, int abstractTypeCount)
        implements Comparable<ComponentComposition> {
    public ComponentComposition {
        Objects.requireNonNull(component, "component");
        if (typeCount < 0 || abstractTypeCount < 0 || abstractTypeCount > typeCount) {
            throw new IllegalArgumentException("invalid component type counts");
        }
    }

    public double abstractness() {
        return typeCount == 0 ? 0.0 : (double) abstractTypeCount / typeCount;
    }

    @Override
    public int compareTo(ComponentComposition other) {
        return component.compareTo(other.component);
    }
}
