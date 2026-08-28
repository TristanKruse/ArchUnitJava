package dev.archunitjava.integration;

import dev.archunitjava.cli.CliGraphFormat;
import dev.archunitjava.cli.CliResultFormat;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Gradle-facing entry point intended for a check task after classes/testClasses. */
public final class GradleBuildIntegration {
    public BuildIntegrationResult check(
            Path projectRoot,
            Path configuration,
            Collection<Path> sourceSetOutputDirectories,
            BuildCommand command,
            Optional<CliResultFormat> resultFormat,
            Optional<CliGraphFormat> graphFormat,
            Appendable standardOut,
            Appendable standardError) {
        return new BuildIntegrationRunner().run(new BuildIntegrationRequest(
                BuildTool.GRADLE,
                "check",
                projectRoot,
                configuration,
                List.copyOf(sourceSetOutputDirectories),
                command,
                resultFormat,
                graphFormat), standardOut, standardError);
    }
}
