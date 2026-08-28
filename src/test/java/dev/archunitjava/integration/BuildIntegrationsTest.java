package dev.archunitjava.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.cli.CliExitCode;
import dev.archunitjava.cli.CliGraphFormat;
import dev.archunitjava.cli.CliResultFormat;
import dev.archunitjava.cli.CliRunner;
import java.io.IOException;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildIntegrationsTest {
    @TempDir Path root;
    private Path apiClasses;
    private Path internalClasses;
    private Path configuration;

    @BeforeEach
    void fixture() throws IOException {
        apiClasses = Files.createDirectories(root.resolve("module-api/classes"));
        internalClasses = Files.createDirectories(root.resolve("module-internal/classes"));
        writeClass(internalClasses, "internal.B", builder -> {});
        writeClass(apiClasses, "api.A", builder ->
                builder.withSuperclass(ClassDesc.of("internal.B")));
        configuration = writeConfiguration(
                "module-internal/classes,module-api/classes", "json", "dot");
    }

    @Test
    void mavenGradleAndDirectCliChecksAreExactlyEquivalent() {
        Invocation direct = direct("check", "--result-format", "json");
        Invocation maven = maven(
                BuildCommand.CHECK, Optional.of(CliResultFormat.JSON), Optional.empty());
        Invocation gradle = gradle(
                BuildCommand.CHECK, Optional.of(CliResultFormat.JSON), Optional.empty());

        assertEquals(CliExitCode.POLICY_VIOLATION.code(), direct.result().exitCode());
        assertEquals(direct.out(), maven.out());
        assertEquals(direct.out(), gradle.out());
        assertEquals(direct.error(), maven.error());
        assertEquals(direct.error(), gradle.error());
        assertEquals(BuildTool.MAVEN, maven.result().tool());
        assertEquals("verify", maven.result().lifecycle());
        assertEquals(BuildTool.GRADLE, gradle.result().tool());
        assertEquals("check", gradle.result().lifecycle());
        assertEquals(List.of(apiClasses, internalClasses),
                maven.result().compiledOutputs());
        assertEquals(maven.result().compiledOutputs(), gradle.result().compiledOutputs());
        assertTrue(maven.out().contains("\"status\":\"FAILED\""));
    }

    @Test
    void graphFormatsMapLosslesslyToTheSharedCliContract() {
        Invocation direct = direct("graph", "--graph-format", "json");
        Invocation maven = maven(
                BuildCommand.GRAPH, Optional.empty(), Optional.of(CliGraphFormat.JSON));
        Invocation gradle = gradle(
                BuildCommand.GRAPH, Optional.empty(), Optional.of(CliGraphFormat.JSON));

        assertEquals(CliExitCode.SUCCESS.code(), maven.result().exitCode());
        assertEquals(direct.out(), maven.out());
        assertEquals(direct.out(), gradle.out());
        assertTrue(maven.out().contains("\"nodes\""));
        assertTrue(maven.out().contains("EXTENDS"));
    }

    @Test
    void missingCompilationAndInputMismatchFailBeforeAnalysis() throws IOException {
        Path empty = Files.createDirectories(root.resolve("empty/classes"));
        Path emptyConfiguration = writeConfiguration("empty/classes", "console", "dot");

        BuildIntegrationException mavenError = assertThrows(
                BuildIntegrationException.class,
                () -> new MavenBuildIntegration().verify(
                        root, emptyConfiguration, List.of(empty), BuildCommand.CHECK,
                        Optional.empty(), Optional.empty(), new StringBuilder(), new StringBuilder()));
        assertTrue(mavenError.getMessage().contains("verify after compile/test-compile"));

        BuildIntegrationException gradleError = assertThrows(
                BuildIntegrationException.class,
                () -> new GradleBuildIntegration().check(
                        root, emptyConfiguration, List.of(empty), BuildCommand.CHECK,
                        Optional.empty(), Optional.empty(), new StringBuilder(), new StringBuilder()));
        assertTrue(gradleError.getMessage().contains("depend on classes/testClasses"));

        BuildIntegrationException mismatch = assertThrows(
                BuildIntegrationException.class,
                () -> new MavenBuildIntegration().verify(
                        root, configuration, List.of(apiClasses), BuildCommand.CHECK,
                        Optional.empty(), Optional.empty(), new StringBuilder(), new StringBuilder()));
        assertTrue(mismatch.getMessage().contains("do not exactly match"));
    }

    @Test
    void invalidFormatOverridesAndEscapingOutputsAreRejected() {
        assertThrows(BuildIntegrationException.class, () -> new BuildIntegrationRequest(
                BuildTool.MAVEN, "verify", root, configuration, List.of(apiClasses),
                BuildCommand.CHECK, Optional.empty(), Optional.of(CliGraphFormat.DOT)));
        assertThrows(BuildIntegrationException.class, () -> new BuildIntegrationRequest(
                BuildTool.GRADLE, "check", root, configuration, List.of(apiClasses),
                BuildCommand.GRAPH, Optional.of(CliResultFormat.JSON), Optional.empty()));

        BuildIntegrationException escape = assertThrows(
                BuildIntegrationException.class,
                () -> new MavenBuildIntegration().verify(
                        root, configuration, List.of(Path.of("..")), BuildCommand.CHECK,
                        Optional.empty(), Optional.empty(), new StringBuilder(), new StringBuilder()));
        assertTrue(escape.getMessage().contains("escapes the approved root"));

        BuildIntegrationException duplicate = assertThrows(
                BuildIntegrationException.class,
                () -> new MavenBuildIntegration().verify(
                        root, configuration, List.of(apiClasses, apiClasses), BuildCommand.CHECK,
                        Optional.empty(), Optional.empty(), new StringBuilder(), new StringBuilder()));
        assertTrue(duplicate.getMessage().contains("same directory more than once"));
    }

    @Test
    void firstPartyContractsDocumentOrderingAndNoNestedBuilds() throws IOException {
        String maven = Files.readString(Path.of("build-integrations/maven/README.md"));
        String gradle = Files.readString(Path.of("build-integrations/gradle/README.md"));

        assertTrue(maven.contains("`verify` phase"));
        assertTrue(maven.contains("never starts Maven"));
        assertTrue(gradle.contains("`classes`"));
        assertTrue(gradle.contains("never starts Gradle"));
    }

    private Invocation maven(
            BuildCommand command,
            Optional<CliResultFormat> resultFormat,
            Optional<CliGraphFormat> graphFormat) {
        StringBuilder out = new StringBuilder();
        StringBuilder error = new StringBuilder();
        BuildIntegrationResult result = new MavenBuildIntegration().verify(
                root, configuration, List.of(internalClasses, apiClasses), command,
                resultFormat, graphFormat, out, error);
        return new Invocation(result, out.toString(), error.toString());
    }

    private Invocation gradle(
            BuildCommand command,
            Optional<CliResultFormat> resultFormat,
            Optional<CliGraphFormat> graphFormat) {
        StringBuilder out = new StringBuilder();
        StringBuilder error = new StringBuilder();
        BuildIntegrationResult result = new GradleBuildIntegration().check(
                root, configuration, List.of(internalClasses, apiClasses), command,
                resultFormat, graphFormat, out, error);
        return new Invocation(result, out.toString(), error.toString());
    }

    private Invocation direct(String command, String option, String value) {
        StringBuilder out = new StringBuilder();
        StringBuilder error = new StringBuilder();
        int exit = new CliRunner().run(new String[] {
                command,
                "--config", configuration.toString(),
                "--root", root.toString(),
                option, value
        }, out, error);
        return new Invocation(
                new BuildIntegrationResult(BuildTool.MAVEN, "direct", exit, List.of()),
                out.toString(), error.toString());
    }

    private Path writeConfiguration(String inputs, String resultFormat, String graphFormat)
            throws IOException {
        Path target = Files.createTempFile(root, "archunitjava-build-", ".properties");
        Files.writeString(target, """
                schema=archunitjava.cli.v1
                inputs=%s
                rules=boundary
                emptySelection=fail
                allowIncompleteAnalysis=false
                resultFormat=%s
                graphFormat=%s
                graphDomain=types
                rule.boundary.domain=types
                rule.boundary.mode=no
                rule.boundary.origins=exact:api.A
                rule.boundary.targets=exact:internal.B
                rule.boundary.self=ignore
                rule.boundary.external=ignore
                rule.boundary.displayName=API boundary
                rule.boundary.rationale=internal implementation must remain private
                rule.boundary.tags=boundary,critical
                rule.boundary.severity=error
                """.formatted(inputs, resultFormat, graphFormat));
        return target;
    }

    private static void writeClass(
            Path output, String binaryName, Consumer<ClassBuilder> transform) throws IOException {
        Path target = output.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, ClassFile.of().build(ClassDesc.of(binaryName), transform));
    }

    private record Invocation(BuildIntegrationResult result, String out, String error) {}
}
