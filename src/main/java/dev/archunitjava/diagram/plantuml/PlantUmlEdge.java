package dev.archunitjava.diagram.plantuml;

import java.util.Objects;

/** One declared directed component edge. */
public record PlantUmlEdge(String originAlias, String targetAlias, PlantUmlArrow arrow)
        implements Comparable<PlantUmlEdge> {
    public PlantUmlEdge {
        originAlias = PlantUmlComponent.alias(originAlias);
        targetAlias = PlantUmlComponent.alias(targetAlias);
        Objects.requireNonNull(arrow, "arrow");
        if (originAlias.equals(targetAlias)) {
            throw new IllegalArgumentException("PlantUML component edges must not be self edges");
        }
    }

    String pairKey() {
        return originAlias + "->" + targetAlias;
    }

    @Override
    public int compareTo(PlantUmlEdge other) {
        int result = originAlias.compareTo(other.originAlias);
        if (result != 0) return result;
        result = targetAlias.compareTo(other.targetAlias);
        return result != 0 ? result : arrow.compareTo(other.arrow);
    }
}
