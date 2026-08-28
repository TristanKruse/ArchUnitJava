package dev.archunitjava.integration;

import dev.archunitjava.cli.CliGraphFormat;
import dev.archunitjava.cli.CliResultFormat;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Maven-facing entry point intended for the verify phase after all module outputs exist. */
public final class MavenBuildIntegration {
    public BuildIntegrationResult verify(
            Path reactorRoot,
            Path configuration,
            Collection<Path> moduleOutputDirectories,
            BuildCommand command,
            Optional<CliResultFormat> resultFormat,
            Optional<CliGraphFormat> graphFormat,
            Appendable standardOut,
            Appendable standardError) {
        return new BuildIntegrationRunner().run(new BuildIntegrationRequest(
                BuildTool.MAVEN,
                "verify",
                reactorRoot,
                configuration,
                List.copyOf(moduleOutputDirectories),
                command,
                resultFormat,
                graphFormat), standardOut, standardError);
    }
}
