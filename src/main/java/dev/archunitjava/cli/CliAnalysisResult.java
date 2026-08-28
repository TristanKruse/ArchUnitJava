package dev.archunitjava.cli;

import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.importer.ImportResolutionResult;
import dev.archunitjava.report.ResultReport;
import java.util.Objects;

/** Shared immutable output used identically by CLI and direct Java callers. */
public record CliAnalysisResult(
        ImportResolutionResult imports, DependencyGraph graph, ResultReport results) {
    public CliAnalysisResult {
        Objects.requireNonNull(imports, "imports");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(results, "results");
    }
}
