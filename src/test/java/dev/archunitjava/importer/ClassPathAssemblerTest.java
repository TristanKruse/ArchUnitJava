package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassPathAssemblerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void classpathSelectionRecordsWinnerAndEveryShadowedAlternative() throws IOException {
        Path first = Files.createDirectory(temporaryDirectory.resolve("first"));
        Path second = Files.createDirectory(temporaryDirectory.resolve("second"));
        writeClass(first.resolve("sample/Duplicate.class"), 1);
        writeClass(second.resolve("sample/Duplicate.class"), 2);
        writeClass(second.resolve("sample/OnlySecond.class"), 3);

        ClassPathAssemblyResult result = new ClassPathAssembler().assemble(List.of(
                ClassFileInput.directory(first), ClassFileInput.directory(second)));

        SelectedClassResource duplicate = result.selections().stream()
                .filter(value -> value.logicalName().equals("sample/Duplicate.class"))
                .findFirst().orElseThrow();
        assertEquals(0, duplicate.winner().precedence());
        assertEquals(List.of(1), duplicate.shadowedAlternatives().stream()
                .map(ClassFileResource::precedence)
                .toList());
        assertEquals(2, result.selections().size());
    }

    @Test
    void modulePathKeepsInputNamespacesSeparateFromClasspathPrecedence() throws IOException {
        Path first = Files.createDirectory(temporaryDirectory.resolve("module-one"));
        Path second = Files.createDirectory(temporaryDirectory.resolve("module-two"));
        writeClass(first.resolve("shared/Type.class"), 1);
        writeClass(second.resolve("shared/Type.class"), 2);
        List<ClassFileInput> inputs = List.of(
                ClassFileInput.directory(first), ClassFileInput.directory(second));

        ClassPathAssemblyResult classpath = new ClassPathAssembler().assemble(inputs);
        ClassPathAssemblyResult modulePath = new ClassPathAssembler(
                        ClassPathAssemblyOptions.modulePathDefaults())
                .assemble(inputs);

        assertEquals(1, classpath.selections().size());
        assertEquals(1, classpath.selections().getFirst().shadowedAlternatives().size());
        assertEquals(2, modulePath.selections().size());
        assertTrue(modulePath.selections().stream()
                .allMatch(value -> value.shadowedAlternatives().isEmpty()));
        assertEquals(2, modulePath.selections().stream()
                .map(SelectedClassResource::lookupScope)
                .distinct()
                .count());
    }

    @Test
    void manifestClassPathIsDisabledByDefaultAndOptInWhenContained() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("libs"));
        Path dependency = jar(root.resolve("dependency.jar"), null, "dep/Type.class");
        Path application = jar(root.resolve("application.jar"), "dependency.jar", "app/Main.class");

        ClassPathAssemblyResult disabled = new ClassPathAssembler()
                .assemble(List.of(ClassFileInput.jar(application)));
        ClassPathAssemblyResult enabled = new ClassPathAssembler(
                        ClassPathAssemblyOptions.classPathDefaults().withManifestClassPath(true))
                .assemble(List.of(ClassFileInput.jar(application)));

        assertEquals(List.of("app/Main.class"), names(disabled));
        assertEquals(List.of("app/Main.class", "dep/Type.class"), names(enabled));
        assertTrue(enabled.selections().stream()
                .filter(value -> value.logicalName().equals("dep/Type.class"))
                .allMatch(value -> value.winner().origin().input().equals(dependency.toString())));
    }

    @Test
    void manifestExpansionIsBoundedAndCannotEscapeItsApprovedDirectory() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("contained"));
        jar(root.resolve("first.jar"), null, "one/Type.class");
        jar(root.resolve("second.jar"), null, "two/Type.class");
        jar(root.resolve("third.jar"), null, "three/Type.class");
        jar(temporaryDirectory.resolve("outside.jar"), null, "outside/Type.class");
        Path application = jar(
                root.resolve("application.jar"),
                "first.jar second.jar ../outside.jar third.jar",
                "app/Main.class");
        ClassPathAssemblyOptions defaults = ClassPathAssemblyOptions.classPathDefaults();
        ClassPathAssemblyOptions bounded = new ClassPathAssemblyOptions(
                ClassPathAssemblyMode.CLASSPATH,
                true,
                3,
                defaults.maximumManifestDepth(),
                defaults.maximumManifestBytes(),
                defaults.enumerationOptions());

        ClassPathAssemblyResult result = new ClassPathAssembler(bounded)
                .assemble(List.of(ClassFileInput.jar(application)));

        assertEquals(List.of("app/Main.class", "one/Type.class", "two/Type.class"), names(result));
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code() == InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED));
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code() == InputDiagnosticCode.MANIFEST_CLASS_PATH_REJECTED));
        assertTrue(result.selections().stream()
                .noneMatch(value -> value.logicalName().equals("outside/Type.class")));
    }

    private Path jar(Path path, String classPath, String... entries) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (classPath != null) {
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        }
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            for (int index = 0; index < entries.length; index++) {
                output.putNextEntry(new JarEntry(entries[index]));
                output.write(index + 1);
                output.closeEntry();
            }
        }
        return path;
    }

    private static void writeClass(Path path, int value) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[] {(byte) value});
    }

    private static List<String> names(ClassPathAssemblyResult result) {
        return result.selections().stream().map(SelectedClassResource::logicalName).toList();
    }
}
