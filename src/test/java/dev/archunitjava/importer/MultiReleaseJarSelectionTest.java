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

class MultiReleaseJarSelectionTest {
    @TempDir Path temporaryDirectory;

    @Test
    void highestEligibleVersionWinsForAnExplicitTargetRelease() throws IOException {
        Path jar = jar(
                "versions.jar",
                "true",
                "sample/Type.class",
                "META-INF/versions/9/sample/Type.class",
                "META-INF/versions/11/sample/Type.class",
                "META-INF/versions/17/sample/Type.class",
                "META-INF/versions/8/sample/TooOld.class",
                "META-INF/versions/not-a-release/sample/Broken.class");

        ClassPathAssemblyResult result = assemble(jar, 11);
        SelectedClassResource selected = result.selections().stream()
                .filter(value -> value.logicalName().equals("sample/Type.class"))
                .findFirst().orElseThrow();

        assertEquals("META-INF/versions/11/sample/Type.class", selected.winner().name());
        assertEquals(11, selected.selectedRelease());
        assertEquals(2, selected.shadowedAlternatives().size());
        assertEquals(3, result.diagnostics().stream()
                .filter(value -> value.code() == InputDiagnosticCode.MULTI_RELEASE_ENTRY_IGNORED)
                .count());
    }

    @Test
    void versionedEntriesAreDisabledUnlessTheManifestSaysTrue() throws IOException {
        Path absent = jar(
                "absent.jar",
                null,
                "sample/Type.class",
                "META-INF/versions/11/sample/Type.class");
        Path falseValue = jar(
                "false.jar",
                "false",
                "sample/Other.class",
                "META-INF/versions/11/sample/Other.class");

        ClassPathAssemblyResult absentResult = assemble(absent, 11);
        ClassPathAssemblyResult falseResult = assemble(falseValue, 11);

        assertEquals("sample/Type.class", absentResult.selections().getFirst().winner().name());
        assertEquals(0, absentResult.selections().getFirst().selectedRelease());
        assertEquals("sample/Other.class", falseResult.selections().getFirst().winner().name());
        assertTrue(absentResult.diagnostics().stream().anyMatch(value ->
                value.context().get("reason").equals("manifest-not-multi-release")));
    }

    @Test
    void multiReleaseManifestValueIsCaseInsensitive() throws IOException {
        Path jar = jar(
                "case.jar",
                "TrUe",
                "sample/Type.class",
                "META-INF/versions/10/sample/Type.class");

        SelectedClassResource selected = assemble(jar, 10).selections().getFirst();

        assertEquals(10, selected.selectedRelease());
        assertEquals("META-INF/versions/10/sample/Type.class", selected.winner().name());
    }

    @Test
    void moduleInfoMayExistOnlyInAnEligibleVersionedArea() throws IOException {
        Path jar = jar(
                "modular.jar",
                "true",
                "base/Java8Type.class",
                "META-INF/versions/9/module-info.class",
                "META-INF/versions/17/module-info.class");

        ClassPathAssemblyResult java11 = assemble(jar, 11);
        SelectedClassResource moduleInfo = java11.selections().stream()
                .filter(value -> value.logicalName().equals("module-info.class"))
                .findFirst().orElseThrow();

        assertEquals(9, moduleInfo.selectedRelease());
        assertEquals("META-INF/versions/9/module-info.class", moduleInfo.winner().name());
        assertTrue(java11.diagnostics().stream().anyMatch(value ->
                value.context().get("entry").equals("META-INF/versions/17/module-info.class")));
    }

    private ClassPathAssemblyResult assemble(Path jar, int targetRelease) {
        ClassPathAssemblyOptions options = ClassPathAssemblyOptions.classPathDefaults()
                .withTargetJavaRelease(targetRelease);
        return new ClassPathAssembler(options).assemble(List.of(ClassFileInput.jar(jar)));
    }

    private Path jar(String name, String multiRelease, String... entries) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (multiRelease != null) {
            manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, multiRelease);
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
}
