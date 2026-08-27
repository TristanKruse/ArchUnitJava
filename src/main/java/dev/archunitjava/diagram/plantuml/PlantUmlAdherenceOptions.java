package dev.archunitjava.diagram.plantuml;

import java.util.Objects;

/** Explicit strictness choices for diagram-to-code comparison. */
public record PlantUmlAdherenceOptions(
        boolean requireDeclaredEdgesObserved,
        UnmappedDiagramDependencyPolicy unmappedDependencyPolicy) {
    public PlantUmlAdherenceOptions {
        Objects.requireNonNull(unmappedDependencyPolicy, "unmappedDependencyPolicy");
    }

    public static PlantUmlAdherenceOptions strict() {
        return new PlantUmlAdherenceOptions(true, UnmappedDiagramDependencyPolicy.FORBID);
    }

    public static PlantUmlAdherenceOptions permissive() {
        return new PlantUmlAdherenceOptions(false, UnmappedDiagramDependencyPolicy.IGNORE);
    }
}
