package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** One type dependency with every duplicate-free declaration source that supports it. */
public record DeclarationDependency(
        JavaTypeName origin,
        JvmReferenceType target,
        DeclarationDependencyEvidenceKind evidenceKind,
        boolean selfDependency,
        boolean externalTarget,
        List<DeclarationDependencySource> sources)
        implements Comparable<DeclarationDependency> {
    public DeclarationDependency {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(evidenceKind, "evidenceKind");
        if (selfDependency != origin.binaryName().equals(target.binaryName())) {
            throw new IllegalArgumentException("selfDependency does not match the endpoints");
        }
        Objects.requireNonNull(sources, "sources");
        sources = sources.stream()
                .map(value -> Objects.requireNonNull(value, "source"))
                .distinct()
                .sorted()
                .toList();
        if (sources.isEmpty()) throw new IllegalArgumentException("Dependency must have a source");
        if (sources.stream().anyMatch(source -> !source.owner().type().equals(origin))) {
            throw new IllegalArgumentException("Every dependency source must belong to its origin");
        }
    }

    @Override
    public int compareTo(DeclarationDependency other) {
        int result = origin.compareTo(other.origin);
        if (result != 0) return result;
        result = target.binaryName().compareTo(other.target.binaryName());
        if (result != 0) return result;
        result = evidenceKind.compareTo(other.evidenceKind);
        if (result != 0) return result;
        result = Boolean.compare(selfDependency, other.selfDependency);
        if (result != 0) return result;
        result = Boolean.compare(externalTarget, other.externalTarget);
        if (result != 0) return result;
        int shared = Math.min(sources.size(), other.sources.size());
        for (int index = 0; index < shared; index++) {
            result = sources.get(index).compareTo(other.sources.get(index));
            if (result != 0) return result;
        }
        return Integer.compare(sources.size(), other.sources.size());
    }
}
