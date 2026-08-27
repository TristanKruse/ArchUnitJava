package dev.archunitjava.rules;

import java.util.List;

/** Detached comparison of declared readability and bytecode-observed module dependencies. */
public record ModuleDependencyComparison(
        List<ModuleDependencyObservation> declaredRequires,
        List<ModuleDependencyObservation> observedDependencies,
        List<ModuleDependencyObservation> observedWithoutRequires,
        List<ModuleDependencyObservation> requiresWithoutObserved,
        int unmappedObservedEdges) {
    public ModuleDependencyComparison {
        declaredRequires = declaredRequires.stream().distinct().sorted().toList();
        observedDependencies = observedDependencies.stream().distinct().sorted().toList();
        observedWithoutRequires = observedWithoutRequires.stream().distinct().sorted().toList();
        requiresWithoutObserved = requiresWithoutObserved.stream().distinct().sorted().toList();
        if (unmappedObservedEdges < 0) {
            throw new IllegalArgumentException("unmappedObservedEdges must not be negative");
        }
    }
}
