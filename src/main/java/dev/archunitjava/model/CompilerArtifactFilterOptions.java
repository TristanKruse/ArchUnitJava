package dev.archunitjava.model;

import java.util.Objects;

/**
 * Presentation-only compiler-artifact filters. Dependency evidence is never filtered by these
 * options; callers use {@link CompilerArtifactView#dependencyEvidence()} for architecture edges.
 */
public record CompilerArtifactFilterOptions(
        boolean includeSyntheticTypes,
        boolean includeSyntheticMembers,
        boolean includeBridgeMembers,
        boolean includeSyntheticParameters,
        boolean includeMandatedParameters,
        boolean includeGeneratedTypes,
        GeneratedCodeOptions generatedCodeOptions) {
    public CompilerArtifactFilterOptions {
        Objects.requireNonNull(generatedCodeOptions, "generatedCodeOptions");
    }

    public static CompilerArtifactFilterOptions bytecodeView() {
        return new CompilerArtifactFilterOptions(
                true, true, true, true, true, true, GeneratedCodeOptions.disabled());
    }

    public static CompilerArtifactFilterOptions sourceView(GeneratedCodeOptions generatedOptions) {
        return new CompilerArtifactFilterOptions(
                false, false, false, false, false, false, generatedOptions);
    }
}
