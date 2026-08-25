package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportOptionsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void orderedRulesUseLastMatchForDirectoriesAndArchives() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("classes"));
        write(directory.resolve("api/Api.class"));
        write(directory.resolve("internal/Drop.class"));
        write(directory.resolve("internal/Keep.class"));
        Path jar = jar(
                "library.jar",
                Map.of(
                        "api/Api.class", new byte[] {1},
                        "internal/Drop.class", new byte[] {2},
                        "internal/Keep.class", new byte[] {3}));
        ImportOptions options = ImportOptions.defaults()
                .withRule(ImportResourceRule.exclude("internal/**"))
                .withRule(ImportResourceRule.include("internal/Keep.class"));

        InputEnumerationResult result = new ClassFileInputEnumerator(options).enumerate(List.of(
                ClassFileInput.directory(directory), ClassFileInput.jar(jar)));

        assertEquals(
                List.of("api/Api.class", "internal/Keep.class", "api/Api.class", "internal/Keep.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        assertEquals(2, result.diagnostics().size());
        assertTrue(result.diagnostics().stream().allMatch(value ->
                value.code() == InputDiagnosticCode.RESOURCE_EXCLUDED
                        && value.context().get("entry").equals("internal/Drop.class")
                        && value.context().get("rule").equals("internal/**")));
    }

    @Test
    void rootArchignoreSupportsNegationAndConfigurationOverridesIt() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("root"));
        Files.writeString(directory.resolve(".archignore"), "**\n!public/**\n", StandardCharsets.UTF_8);
        write(directory.resolve("public/Api.class"));
        write(directory.resolve("private/Recovered.class"));
        write(directory.resolve("private/Drop.class"));
        ImportOptions options = ImportOptions.defaults()
                .withRule(ImportResourceRule.include("private/Recovered.class"));

        InputEnumerationResult result = new ClassFileInputEnumerator(options)
                .enumerate(List.of(ClassFileInput.directory(directory)));

        assertEquals(
                List.of("private/Recovered.class", "public/Api.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        InputDiagnostic exclusion = result.diagnostics().getFirst();
        assertEquals(InputDiagnosticCode.RESOURCE_EXCLUDED, exclusion.code());
        assertEquals("private/Drop.class", exclusion.context().get("entry"));
        assertEquals("1", exclusion.context().get("line"));
        assertTrue(exclusion.context().get("source").endsWith(".archignore"));
    }

    @Test
    void archiveArchignoreUsesTheSameRootRelativeSemantics() throws IOException {
        Path jar = jar(
                "ignored.jar",
                Map.of(
                        ".archignore", "hidden/**\n".getBytes(StandardCharsets.UTF_8),
                        "hidden/Internal.class", new byte[] {1},
                        "visible/Api.class", new byte[] {2}));

        InputEnumerationResult result = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.jar(jar)));

        assertEquals(List.of("visible/Api.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        assertTrue(result.diagnostics().getFirst().context().get("source").endsWith("!/.archignore"));
    }

    @Test
    void ignoreFilesRejectEscapesExpansionsAndCommandTokensAsDataErrors() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("hostile"));
        Files.writeString(
                directory.resolve(".archignore"),
                "../Outside.class\n${HOME}/Secret.class\nC:/Absolute.class\n| command\nok/**\n",
                StandardCharsets.UTF_8);
        write(directory.resolve("ok/Drop.class"));
        write(directory.resolve("safe/Keep.class"));

        InputEnumerationResult result = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(directory)));

        assertEquals(List.of("safe/Keep.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        assertEquals(4, result.diagnostics().stream()
                .filter(value -> value.code() == InputDiagnosticCode.INVALID_IGNORE_RULE)
                .count());
        assertEquals(1, result.diagnostics().stream()
                .filter(value -> value.code() == InputDiagnosticCode.RESOURCE_EXCLUDED)
                .count());
    }

    @Test
    void scopeIsAHardBoundaryAndOptionsRemainImmutableAndCacheable() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("scoped"));
        write(directory.resolve("prod/Api.class"));
        write(directory.resolve("test/Fixture.class"));
        ImportOptions defaults = ImportOptions.defaults();
        ImportOptions scoped = defaults
                .withScope(ImportScope.matching("production", "prod/**"))
                .withRule(ImportResourceRule.include("test/**"));

        InputEnumerationResult result = new ClassFileInputEnumerator(scoped)
                .enumerate(List.of(ClassFileInput.directory(directory)));

        assertEquals(List.of("prod/Api.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        assertEquals("scope:production", result.diagnostics().getFirst().context().get("rule"));
        assertTrue(defaults.rules().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> scoped.rules().clear());
        assertNotEquals(defaults.fingerprintMaterial(), scoped.fingerprintMaterial());
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path jar = temporaryDirectory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }

    private static void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[] {1});
    }
}
