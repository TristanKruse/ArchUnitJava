package dev.archunitjava.metrics;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Explicit source-count policy. Defaults exclude synthetic/bridge members, conservative generated
 * types/documents, and package-info metadata types. Record components are counted separately;
 * actual record fields/accessors in the class file remain ordinary members unless synthetic.
 */
public record SourceMetricOptions(
        boolean includeSyntheticMembers,
        boolean includeGeneratedTypes,
        boolean includePackageInfoTypes,
        Set<String> generatedAnnotationBinaryNames) {
    public SourceMetricOptions {
        Objects.requireNonNull(generatedAnnotationBinaryNames, "generatedAnnotationBinaryNames");
        TreeSet<String> names = new TreeSet<>();
        for (String name : generatedAnnotationBinaryNames) {
            if (name == null || name.isBlank() || name.indexOf('/') >= 0) {
                throw new IllegalArgumentException("generated annotation names must be binary names");
            }
            names.add(name);
        }
        generatedAnnotationBinaryNames = java.util.Collections.unmodifiableSet(names);
    }

    public static SourceMetricOptions defaults() {
        return new SourceMetricOptions(false, false, false, Set.of());
    }
}
