package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyKind;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** One detached declared or observed module dependency with its evidence domain kept explicit. */
public record ModuleDependencyObservation(
        ModuleIdentityId origin,
        String targetModule,
        String evidenceDomain,
        Set<DependencyKind> dependencyKinds,
        List<DependencyEvidence> evidence)
        implements Comparable<ModuleDependencyObservation> {
    public ModuleDependencyObservation {
        Objects.requireNonNull(origin, "origin");
        if (targetModule == null || targetModule.isBlank()) {
            throw new IllegalArgumentException("targetModule must not be blank");
        }
        if (evidenceDomain == null || evidenceDomain.isBlank()) {
            throw new IllegalArgumentException("evidenceDomain must not be blank");
        }
        dependencyKinds = Set.copyOf(new TreeSet<>(
                Objects.requireNonNull(dependencyKinds, "dependencyKinds")));
        evidence = evidence.stream().distinct().sorted().toList();
    }

    public String comparisonKey() {
        return origin.stableKey() + "->" + targetModule;
    }

    @Override
    public int compareTo(ModuleDependencyObservation other) {
        int result = comparisonKey().compareTo(other.comparisonKey());
        return result != 0 ? result : evidenceDomain.compareTo(other.evidenceDomain);
    }
}
