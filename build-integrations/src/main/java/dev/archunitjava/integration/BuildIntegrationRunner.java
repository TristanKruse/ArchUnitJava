package dev.archunitjava.integration;

import dev.archunitjava.cli.CliConfiguration;
import dev.archunitjava.cli.CliConfigurationLoader;
import dev.archunitjava.cli.CliGraphFormat;
import dev.archunitjava.cli.CliResultFormat;
import dev.archunitjava.cli.CliRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Executes the shared contract against already-compiled outputs.
 *
 * <p>This class has no process-launching API and never invokes Maven, Gradle, compilers, target
 * classes, plugins, or annotation processors.
 */
public final class BuildIntegrationRunner {
    public BuildIntegrationResult run(
            BuildIntegrationRequest request, Appendable standardOut, Appendable standardError) {
        BuildIntegrationRequest value = Objects.requireNonNull(request, "request");
        try {
            Path root = value.approvedRoot().toRealPath();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new BuildIntegrationException("Approved build root must be a directory");
            }
            List<Path> outputs = outputs(root, value.compiledOutputs(), value.tool());
            CliConfiguration configuration = CliConfigurationLoader.load(
                    value.configuration(), root);
            if (!configuration.inputs().equals(outputs)) {
                throw new BuildIntegrationException(
                        "Compiled outputs do not exactly match the validated CLI inputs");
            }
            List<String> arguments = new ArrayList<>(List.of(
                    value.command().cliName(),
                    "--config", configuration.source().toString(),
                    "--root", root.toString()));
            value.resultFormat().ifPresent(format -> {
                arguments.add("--result-format");
                arguments.add(resultFormat(format));
            });
            value.graphFormat().ifPresent(format -> {
                arguments.add("--graph-format");
                arguments.add(format.name().toLowerCase(java.util.Locale.ROOT));
            });
            int exit = new CliRunner().run(
                    arguments.toArray(String[]::new), standardOut, standardError);
            return new BuildIntegrationResult(value.tool(), value.lifecycle(), exit, outputs);
        } catch (BuildIntegrationException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new BuildIntegrationException(
                    value.tool() + " integration could not validate compiled outputs: "
                            + safeMessage(error), error);
        }
    }

    private static List<Path> outputs(
            Path root, List<Path> configured, BuildTool tool) throws IOException {
        TreeSet<Path> sorted = new TreeSet<>();
        for (Path output : configured) {
            Path candidate = (output.isAbsolute() ? output : root.resolve(output)).normalize();
            if (!candidate.startsWith(root)) {
                throw new BuildIntegrationException("Compiled output escapes the approved root");
            }
            Path resolved = candidate.toRealPath();
            if (!resolved.startsWith(root) || !Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
                throw new BuildIntegrationException(
                        "Compiled output must be a directory inside the approved root: " + output);
            }
            if (!containsClass(resolved)) {
                String ordering = tool == BuildTool.MAVEN
                        ? "Bind the architecture check to verify after compile/test-compile."
                        : "Make the architecture check task depend on classes/testClasses.";
                throw new BuildIntegrationException(
                        "Compiled output contains no .class files: " + output + ". " + ordering);
            }
            if (!sorted.add(resolved)) {
                throw new BuildIntegrationException(
                        "Compiled outputs resolve to the same directory more than once: " + output);
            }
        }
        return List.copyOf(sorted);
    }

    private static boolean containsClass(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.limit(100_001)
                    .anyMatch(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && path.getFileName().toString().endsWith(".class"));
        }
    }

    private static String resultFormat(CliResultFormat format) {
        return format == CliResultFormat.JUNIT_XML
                ? "junit-xml" : format.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return (message == null || message.isBlank() ? error.getClass().getSimpleName() : message)
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
