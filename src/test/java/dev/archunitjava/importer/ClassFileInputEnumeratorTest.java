package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileInputEnumeratorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void enumeratesDirectoriesAndJarsInStablePrecedenceOrder() throws IOException {
        Path classes = Files.createDirectories(temporaryDirectory.resolve("classes"));
        writeClass(classes.resolve("z/Z.class"), 1);
        writeClass(classes.resolve("a/A.class"), 2);
        Files.writeString(classes.resolve("ignored.txt"), "not bytecode");
        Path jar = jar("library.jar", "b/B.class", "a/A.class");

        InputEnumerationResult result = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(classes), ClassFileInput.jar(jar)));

        assertEquals(
                List.of("a/A.class", "z/Z.class", "a/A.class", "b/B.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        assertEquals(List.of(0, 0, 1, 1),
                result.resources().stream().map(ClassFileResource::precedence).toList());
        assertEquals(ClassFileInput.Kind.DIRECTORY, result.resources().getFirst().origin().kind());
        assertEquals(ClassFileInput.Kind.JAR, result.resources().getLast().origin().kind());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void autoDetectsSupportedInputsAndDiagnosesUnsupportedOnes() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("classes"));
        writeClass(directory.resolve("A.class"), 1);
        Path jar = jar("types.zip", "B.class");
        Path text = Files.writeString(temporaryDirectory.resolve("classes.txt"), "no");

        InputEnumerationResult result = new ClassFileInputEnumerator().enumerate(List.of(
                ClassFileInput.path(directory), ClassFileInput.path(jar), ClassFileInput.path(text)));

        assertEquals(List.of("A.class", "B.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        assertEquals(List.of(InputDiagnosticCode.UNSUPPORTED_INPUT), codes(result));
    }

    @Test
    void reportsMissingAndDuplicateInputsWithoutDuplicatingResources() throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));
        writeClass(classes.resolve("A.class"), 1);

        InputEnumerationResult result = new ClassFileInputEnumerator().enumerate(List.of(
                ClassFileInput.directory(classes),
                ClassFileInput.path(classes),
                ClassFileInput.path(temporaryDirectory.resolve("missing"))));

        assertEquals(1, result.resources().size());
        assertEquals(
                List.of(InputDiagnosticCode.DUPLICATE_INPUT, InputDiagnosticCode.MISSING_INPUT),
                codes(result));
    }

    @Test
    void resourceBoundsStopTraversalDeterministically() throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));
        writeClass(classes.resolve("A.class"), 1);
        writeClass(classes.resolve("B.class"), 2);
        var options = new InputEnumerationOptions(1, 8, 8, 8);

        InputEnumerationResult result = new ClassFileInputEnumerator(options)
                .enumerate(List.of(ClassFileInput.directory(classes)));

        assertEquals(List.of("A.class"), result.resources().stream().map(ClassFileResource::name).toList());
        assertEquals(List.of(InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED), codes(result));
    }

    @Test
    void jarEntryBoundsApplyBeforeClassFiltering() throws IOException {
        Path jar = jar("many.jar", "a.txt", "b.txt", "C.class");
        var options = new InputEnumerationOptions(8, 8, 8, 2);

        InputEnumerationResult result = new ClassFileInputEnumerator(options)
                .enumerate(List.of(ClassFileInput.jar(jar)));

        assertTrue(result.resources().isEmpty());
        assertEquals(List.of(InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED), codes(result));
    }

    @Test
    void rejectsArchiveNamesThatCouldEscapeOrChangeMeaningAcrossPlatforms()
            throws IOException {
        Path jar = jar("hostile.jar", "../Outside.class", "windows\\Alias.class", "/Root.class");

        InputEnumerationResult result = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.jar(jar)));

        assertTrue(result.resources().isEmpty());
        assertEquals(3, result.diagnostics().size());
        assertTrue(result.diagnostics().stream()
                .allMatch(diagnostic -> diagnostic.code() == InputDiagnosticCode.INVALID_RESOURCE_NAME));
    }

    @Test
    void directoryEntryBoundsFailWithoutReturningEncounterOrderDependentPartialData()
            throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("bounded"));
        writeClass(classes.resolve("A.class"), 1);
        writeClass(classes.resolve("B.class"), 2);
        var options = new InputEnumerationOptions(8, 8, 2, 8);

        InputEnumerationResult result = new ClassFileInputEnumerator(options)
                .enumerate(List.of(ClassFileInput.directory(classes)));

        assertTrue(result.resources().isEmpty());
        assertEquals(List.of(InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED), codes(result));
        assertEquals("directory-entries:2", result.diagnostics().getFirst().context().get("limit"));
    }

    @Test
    void symbolicLinksAreNeverFollowed() throws IOException {
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        writeClass(outside.resolve("Outside.class"), 1);
        Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));
        Path link = classes.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return;
        }

        InputEnumerationResult result = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(classes)));

        assertTrue(result.resources().isEmpty());
        assertEquals(List.of(InputDiagnosticCode.SYMLINK_SKIPPED), codes(result));
    }

    @Test
    void canEnumerateAnExplicitRuntimeModule() {
        InputEnumerationResult result = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.jrtModule("java.logging")));

        assertFalse(result.resources().isEmpty());
        assertTrue(result.resources().stream()
                .allMatch(resource -> resource.origin().kind() == ClassFileInput.Kind.JRT_MODULE));
        assertTrue(result.resources().stream().anyMatch(resource -> resource.name().equals("module-info.class")));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void missingAndInvalidRuntimeModulesAreTypedDiagnostics() {
        InputEnumerationResult result = new ClassFileInputEnumerator().enumerate(List.of(
                ClassFileInput.jrtModule("not.a.real.module"), ClassFileInput.jrtModule("../java.base")));

        assertEquals(
                List.of(InputDiagnosticCode.MISSING_INPUT, InputDiagnosticCode.UNSUPPORTED_INPUT),
                codes(result));
    }

    private Path jar(String name, String... entries) throws IOException {
        Path jar = temporaryDirectory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (int index = 0; index < entries.length; index++) {
                output.putNextEntry(new JarEntry(entries[index]));
                output.write(index + 1);
                output.closeEntry();
            }
        }
        return jar;
    }

    private static void writeClass(Path path, int value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[] {(byte) value});
    }

    private static List<InputDiagnosticCode> codes(InputEnumerationResult result) {
        return result.diagnostics().stream().map(InputDiagnostic::code).toList();
    }
}
