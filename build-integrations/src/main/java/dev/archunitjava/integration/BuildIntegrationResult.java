package dev.archunitjava.integration;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Build-host result retaining the deterministic compiled-output order. */
public record BuildIntegrationResult(
        BuildTool tool, String lifecycle, int exitCode, List<Path> compiledOutputs) {
    public BuildIntegrationResult {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(lifecycle, "lifecycle");
        compiledOutputs = List.copyOf(Objects.requireNonNull(compiledOutputs, "compiledOutputs"));
    }
}
