package dev.archunitjava.diagram.plantuml;

import dev.archunitjava.graph.StableId;

/** Stable result identity for a diagram component alias. */
public record PlantUmlComponentId(String alias) implements StableId {
    public PlantUmlComponentId {
        alias = PlantUmlComponent.alias(alias);
    }

    @Override
    public String stableKey() {
        return "plantuml-component:" + alias;
    }
}
