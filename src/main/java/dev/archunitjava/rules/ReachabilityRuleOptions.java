package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Explicit dependency semantics and finding bounds for reachability rules. */
public record ReachabilityRuleOptions(
        ReachabilityAssumption assumption,
        Set<DependencyKind> includedKinds,
        int maximumSubjectsPerRegion,
        int maximumEvidenceEntries) {
    private static final int DEFAULT_SUBJECTS = 32;
    private static final int DEFAULT_EVIDENCE = 64;

    public ReachabilityRuleOptions {
        Objects.requireNonNull(assumption, "assumption");
        Objects.requireNonNull(includedKinds, "includedKinds");
        includedKinds = includedKinds.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(includedKinds));
        if (maximumSubjectsPerRegion <= 0 || maximumEvidenceEntries <= 0) {
            throw new IllegalArgumentException("Reachability bounds must be positive");
        }
    }

    public static ReachabilityRuleOptions publicLibraryDefaults() {
        return defaults(ReachabilityAssumption.PUBLIC_LIBRARY_CONSERVATIVE);
    }

    public static ReachabilityRuleOptions configuredEntryPoints() {
        return defaults(ReachabilityAssumption.CONFIGURED_ENTRY_POINTS);
    }

    public ReachabilityRuleOptions includingOnly(Set<DependencyKind> kinds) {
        return new ReachabilityRuleOptions(
                assumption, kinds, maximumSubjectsPerRegion, maximumEvidenceEntries);
    }

    public ReachabilityRuleOptions withBounds(int subjectsPerRegion, int evidenceEntries) {
        return new ReachabilityRuleOptions(
                assumption, includedKinds, subjectsPerRegion, evidenceEntries);
    }

    private static ReachabilityRuleOptions defaults(ReachabilityAssumption assumption) {
        return new ReachabilityRuleOptions(
                assumption,
                EnumSet.allOf(DependencyKind.class),
                DEFAULT_SUBJECTS,
                DEFAULT_EVIDENCE);
    }
}
