package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** One selected logical class resource plus every lower-precedence alternative. */
public record SelectedClassResource(
        String lookupScope,
        String logicalName,
        ClassFileResource winner,
        List<ClassFileResource> shadowedAlternatives)
        implements Comparable<SelectedClassResource> {
    public SelectedClassResource {
        if (lookupScope == null || lookupScope.isBlank()) {
            throw new IllegalArgumentException("lookupScope must not be blank");
        }
        if (logicalName == null || logicalName.isBlank() || !logicalName.endsWith(".class")) {
            throw new IllegalArgumentException("logicalName must identify a class resource");
        }
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(shadowedAlternatives, "shadowedAlternatives");
        shadowedAlternatives = shadowedAlternatives.stream()
                .map(value -> Objects.requireNonNull(value, "shadowedAlternative"))
                .sorted()
                .toList();
        if (shadowedAlternatives.stream().anyMatch(value -> value == winner)) {
            throw new IllegalArgumentException("The winner cannot shadow itself");
        }
    }

    @Override
    public int compareTo(SelectedClassResource other) {
        int result = lookupScope.compareTo(other.lookupScope);
        return result != 0 ? result : logicalName.compareTo(other.logicalName);
    }
}
