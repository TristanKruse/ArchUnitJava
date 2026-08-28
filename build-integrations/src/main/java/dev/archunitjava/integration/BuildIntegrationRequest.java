package dev.archunitjava.integration;

import dev.archunitjava.cli.CliGraphFormat;
import dev.archunitjava.cli.CliResultFormat;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Lossless mapping from build-tool inputs to the shared CLI/library contract. */
public record BuildIntegrationRequest(
        BuildTool tool,
        String lifecycle,
        Path approvedRoot,
        Path configuration,
        List<Path> compiledOutputs,
        BuildCommand command,
        Optional<CliResultFormat> resultFormat,
        Optional<CliGraphFormat> graphFormat) {
    public BuildIntegrationRequest {
        Objects.requireNonNull(tool, "tool");
        if (lifecycle == null || lifecycle.isBlank()) {
            throw new IllegalArgumentException("lifecycle must not be blank");
        }
        Objects.requireNonNull(approvedRoot, "approvedRoot");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(compiledOutputs, "compiledOutputs");
        compiledOutputs = List.copyOf(compiledOutputs);
        if (compiledOutputs.isEmpty() || compiledOutputs.size() > 256) {
            throw new BuildIntegrationException(
                    "Build integration requires between 1 and 256 compiled outputs");
        }
        compiledOutputs.forEach(output -> Objects.requireNonNull(output, "compiledOutput"));
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(resultFormat, "resultFormat");
        Objects.requireNonNull(graphFormat, "graphFormat");
        if (command == BuildCommand.CHECK && graphFormat.isPresent()) {
            throw new BuildIntegrationException("A check command cannot override graph format");
        }
        if (command == BuildCommand.GRAPH && resultFormat.isPresent()) {
            throw new BuildIntegrationException("A graph command cannot override result format");
        }
    }
}
