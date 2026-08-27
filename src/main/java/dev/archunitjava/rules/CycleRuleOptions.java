package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Explicit dependency semantics and output bounds for type and package cycle rules. */
public record CycleRuleOptions(
        Set<DependencyKind> includedKinds,
        TypeUseDependencyPolicy typeUseDependencies,
        SyntheticEdgePolicy syntheticEdges,
        int maximumRepresentativeLength,
        long maximumTraversedEdgesPerComponent,
        int maximumEvidenceEntries) {
    private static final int DEFAULT_REPRESENTATIVE_LENGTH = 64;
    private static final long DEFAULT_TRAVERSED_EDGES = 10_000;
    private static final int DEFAULT_EVIDENCE_ENTRIES = 64;

    public CycleRuleOptions {
        Objects.requireNonNull(includedKinds, "includedKinds");
        EnumSet<DependencyKind> kinds = includedKinds.isEmpty()
                ? EnumSet.noneOf(DependencyKind.class)
                : EnumSet.copyOf(includedKinds);
        includedKinds = Collections.unmodifiableSet(kinds);
        Objects.requireNonNull(typeUseDependencies, "typeUseDependencies");
        Objects.requireNonNull(syntheticEdges, "syntheticEdges");
        if (maximumRepresentativeLength <= 0) {
            throw new IllegalArgumentException("maximumRepresentativeLength must be positive");
        }
        if (maximumTraversedEdgesPerComponent <= 0) {
            throw new IllegalArgumentException("maximumTraversedEdgesPerComponent must be positive");
        }
        if (maximumEvidenceEntries <= 0) {
            throw new IllegalArgumentException("maximumEvidenceEntries must be positive");
        }
    }

    public static CycleRuleOptions defaults() {
        return new CycleRuleOptions(
                EnumSet.allOf(DependencyKind.class),
                TypeUseDependencyPolicy.INCLUDE,
                SyntheticEdgePolicy.IGNORE,
                DEFAULT_REPRESENTATIVE_LENGTH,
                DEFAULT_TRAVERSED_EDGES,
                DEFAULT_EVIDENCE_ENTRIES);
    }

    public CycleRuleOptions includingOnly(Set<DependencyKind> kinds) {
        return new CycleRuleOptions(
                kinds,
                typeUseDependencies,
                syntheticEdges,
                maximumRepresentativeLength,
                maximumTraversedEdgesPerComponent,
                maximumEvidenceEntries);
    }

    public CycleRuleOptions withTypeUseDependencies(TypeUseDependencyPolicy policy) {
        return new CycleRuleOptions(
                includedKinds,
                policy,
                syntheticEdges,
                maximumRepresentativeLength,
                maximumTraversedEdgesPerComponent,
                maximumEvidenceEntries);
    }

    public CycleRuleOptions withSyntheticEdges(SyntheticEdgePolicy policy) {
        return new CycleRuleOptions(
                includedKinds,
                typeUseDependencies,
                policy,
                maximumRepresentativeLength,
                maximumTraversedEdgesPerComponent,
                maximumEvidenceEntries);
    }

    public CycleRuleOptions withBounds(
            int representativeLength, long traversedEdgesPerComponent, int evidenceEntries) {
        return new CycleRuleOptions(
                includedKinds,
                typeUseDependencies,
                syntheticEdges,
                representativeLength,
                traversedEdgesPerComponent,
                evidenceEntries);
    }
}
