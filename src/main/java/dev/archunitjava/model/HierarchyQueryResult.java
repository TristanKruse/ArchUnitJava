package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Bounded transitive hierarchy result with explicit gaps and cycle evidence. */
public record HierarchyQueryResult(
        List<JavaTypeName> supertypes,
        List<JavaTypeName> missingTypes,
        boolean complete,
        boolean cycleDetected) {
    public HierarchyQueryResult {
        supertypes = sorted(supertypes, "supertype");
        missingTypes = sorted(missingTypes, "missingType");
        if ((!missingTypes.isEmpty() || cycleDetected) && complete) {
            throw new IllegalArgumentException("Incomplete or cyclic results cannot be complete");
        }
    }

    private static List<JavaTypeName> sorted(List<JavaTypeName> values, String name) {
        Objects.requireNonNull(values, name + "s");
        TreeSet<JavaTypeName> sorted = new TreeSet<>();
        values.forEach(value -> sorted.add(Objects.requireNonNull(value, name)));
        return List.copyOf(sorted);
    }
}
