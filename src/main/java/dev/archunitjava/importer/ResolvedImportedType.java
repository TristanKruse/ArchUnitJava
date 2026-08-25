package dev.archunitjava.importer;

import dev.archunitjava.model.JavaType;
import java.util.List;
import java.util.Objects;

/** Winning imported type within one lookup scope plus all conflicting definitions. */
public record ResolvedImportedType(
        String lookupScope, JavaType winner, List<ShadowedTypeDefinition> shadowedDefinitions)
        implements Comparable<ResolvedImportedType> {
    public ResolvedImportedType {
        if (lookupScope == null || lookupScope.isBlank()) {
            throw new IllegalArgumentException("lookupScope must not be blank");
        }
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(shadowedDefinitions, "shadowedDefinitions");
        shadowedDefinitions = shadowedDefinitions.stream()
                .map(value -> Objects.requireNonNull(value, "shadowedDefinition"))
                .sorted()
                .toList();
    }

    @Override
    public int compareTo(ResolvedImportedType other) {
        int result = lookupScope.compareTo(other.lookupScope);
        return result != 0 ? result : winner.compareTo(other.winner);
    }
}
