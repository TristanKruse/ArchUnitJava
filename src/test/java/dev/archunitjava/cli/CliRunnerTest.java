package dev.archunitjava.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.report.ResultJsonRenderer;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassBuilder;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliRunnerTest {
    @TempDir Path root;
    private Path classes;
    private Path configuration;

    @BeforeEach
    void fixture() throws IOException {
        classes = Files.createDirectories(root.resolve("classes"));
        writeClass("internal.B", builder -> {});
        writeClass("api.A", builder -> builder.withSuperclass(ClassDesc.of("internal.B")));
        configuration = writeConfiguration("""
                schema=archunitjava.cli.v1
                inputs=classes
                rules=boundary
                emptySelection=fail
                allowIncompleteAnalysis=false
                resultFormat=console
                graphFormat=dot
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
                """);
    }

    @Test
    void checkUsesTheSameResultsAsThePublicJavaApi() {
        CliConfiguration config = CliConfigurationLoader.load(configuration, root);
        String expected = ResultJsonRenderer.render(new CliAnalyzer().analyze(config).results());
        StringBuilder out = new StringBuilder();
        StringBuilder error = new StringBuilder();

        int exit = new CliRunner().run(new String[] {
                "check", "--config", configuration.toString(), "--root", root.toString(),
                "--result-format", "json"
        }, out, error);

        assertEquals(CliExitCode.POLICY_VIOLATION.code(), exit);
        assertEquals(expected, out.toString());
        assertTrue(out.toString().contains("\"status\":\"FAILED\""));
        assertTrue(error.isEmpty());
    }

    @Test
    void graphExplainAndValidationCommandsAreStableAndBounded() {
        Invocation validation = run("validate-config");
        assertEquals(CliExitCode.SUCCESS.code(), validation.exit());
        assertEquals("configuration valid: archunitjava.cli.v1\n", validation.out());

        Invocation explanation = run("explain");
        assertEquals(CliExitCode.SUCCESS.code(), explanation.exit());
        assertTrue(explanation.out().contains(
                "rule boundary: domain=TYPES mode=NO origins=exact:api.A targets=exact:internal.B"));
        assertFalse(explanation.out().contains(root.toString()));

        Invocation graph = run("graph", "--graph-format", "dot");
        assertEquals(CliExitCode.SUCCESS.code(), graph.exit());
        assertTrue(graph.out().startsWith("digraph architecture"));
        assertTrue(graph.out().contains("EXTENDS"));
    }

    @Test
    void configurationCannotEscapeRootsDuplicateKeysOrRequestExecutableFactories()
            throws IOException {
        Path traversal = writeConfiguration("""
                schema=archunitjava.cli.v1
                inputs=../outside
                rules=one
                rule.one.domain=types
                rule.one.mode=no
                rule.one.origins=exact:a.A
                rule.one.targets=exact:b.B
                """);
        assertThrows(CliConfigurationException.class,
                () -> CliConfigurationLoader.load(traversal, root));

        Path duplicate = writeConfiguration("""
                schema=archunitjava.cli.v1
                inputs=classes
                inputs=classes
                rules=one
                rule.one.domain=types
                rule.one.mode=no
                rule.one.origins=exact:a.A
                rule.one.targets=exact:b.B
                """);
        assertThrows(CliConfigurationException.class,
                () -> CliConfigurationLoader.load(duplicate, root));

        Path executable = writeConfiguration("""
                schema=archunitjava.cli.v1
                inputs=classes
                rules=one
                rule.one.domain=types
                rule.one.mode=no
                rule.one.origins=exact:a.A
                rule.one.targets=exact:b.B
                rule.one.factory=java.lang.Runtime
                command=calc.exe
                """);
        CliConfigurationException error = assertThrows(
                CliConfigurationException.class,
                () -> CliConfigurationLoader.load(executable, root));
        assertTrue(error.getMessage().contains("Unknown"));
    }

    @Test
    void documentedExitCodesSeparateUsageConfigurationAnalysisAndPolicy() throws IOException {
        Invocation usage = invoke("unknown");
        assertEquals(CliExitCode.USAGE.code(), usage.exit());
        assertTrue(usage.error().contains("Exit codes:"));

        Invocation invalid = invoke(
                "check", "--config", root.resolve("missing.properties").toString(),
                "--root", root.toString());
        assertEquals(CliExitCode.INVALID_CONFIGURATION.code(), invalid.exit());

        Path brokenClasses = Files.createDirectories(root.resolve("broken"));
        Files.write(brokenClasses.resolve("Broken.class"), new byte[] {0, 1, 2, 3});
        Path broken = writeConfiguration("""
                schema=archunitjava.cli.v1
                inputs=broken
                rules=one
                rule.one.domain=types
                rule.one.mode=no
                rule.one.origins=exact:a.A
                rule.one.targets=exact:b.B
                rule.one.external=ignore
                """);
        Invocation analysis = invoke(
                "check", "--config", broken.toString(), "--root", root.toString());
        assertEquals(CliExitCode.ANALYSIS_ERROR.code(), analysis.exit());

        assertTrue(CliExitCode.documentation().contains(
                "5  one or more architecture policies failed"));
    }

    private Invocation run(String command, String... extra) {
        String[] arguments = new String[5 + extra.length];
        arguments[0] = command;
        arguments[1] = "--config";
        arguments[2] = configuration.toString();
        arguments[3] = "--root";
        arguments[4] = root.toString();
        System.arraycopy(extra, 0, arguments, 5, extra.length);
        return invoke(arguments);
    }

    private Invocation invoke(String... arguments) {
        StringBuilder out = new StringBuilder();
        StringBuilder error = new StringBuilder();
        int exit = new CliRunner().run(arguments, out, error);
        return new Invocation(exit, out.toString(), error.toString());
    }

    private Path writeConfiguration(String text) throws IOException {
        Path file = Files.createTempFile(root, "archunitjava-", ".properties");
        Files.writeString(file, text);
        return file;
    }

    private void writeClass(String binaryName, Consumer<ClassBuilder> transform) throws IOException {
        Path target = classes.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, ClassFile.of().build(ClassDesc.of(binaryName), transform));
    }

    private record Invocation(int exit, String out, String error) {}
}
