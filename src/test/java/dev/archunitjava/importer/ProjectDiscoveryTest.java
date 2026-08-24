package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectDiscoveryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void explicitRootWinsOverCloserAutomaticCandidates() throws IOException {
        Path explicitRoot = directory("workspace");
        writePom(explicitRoot, "");
        Path nested = directory("workspace/nested/src/main/java");
        Files.writeString(explicitRoot.resolve("nested/build.gradle"), "plugins { id('java') }");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(nested,
                ProjectDiscoveryOptions.builder().explicitRoot(explicitRoot).build());

        assertTrue(result.found());
        assertEquals(explicitRoot.toAbsolutePath(), result.project().orElseThrow().root());
        assertEquals(BuildSystem.MAVEN, result.project().orElseThrow().buildSystem());
        assertEquals(List.of(explicitRoot.resolve("target/classes").toAbsolutePath()),
                result.project().orElseThrow().mainClassDirectories());
    }

    @Test
    void ancestorSearchIsBoundedAndStartingFilesUseTheirParent() throws IOException {
        Path root = directory("bounded");
        writePom(root, "");
        Path source = directory("bounded/a/b/c").resolve("Type.java");
        Files.writeString(source, "final class Type {}");

        ProjectDiscoveryResult tooShallow = ProjectDiscovery.discover(source,
                ProjectDiscoveryOptions.builder().maxAncestorDepth(2).build());
        ProjectDiscoveryResult sufficient = ProjectDiscovery.discover(source,
                ProjectDiscoveryOptions.builder().maxAncestorDepth(4).build());

        assertFalse(tooShallow.found());
        assertTrue(hasDiagnostic(tooShallow, DiscoveryDiagnosticCode.NO_PROJECT_FOUND));
        assertEquals(root.toAbsolutePath(), sufficient.project().orElseThrow().root());
    }

    @Test
    void mavenStaticOutputDirectoriesAreReadWithoutRunningMaven() throws IOException {
        Path root = directory("maven-layout");
        writePom(root, """
                <build>
                  <outputDirectory>out/main</outputDirectory>
                  <testOutputDirectory>out/test</testOutputDirectory>
                </build>
                """);
        Path sentinel = root.resolve("wrapper-ran.txt");
        Files.writeString(root.resolve("mvnw.cmd"), "@echo ran > wrapper-ran.txt");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        DiscoveredProject project = result.project().orElseThrow();
        assertEquals(List.of(root.resolve("out/main").toAbsolutePath()), project.mainClassDirectories());
        assertEquals(List.of(root.resolve("out/test").toAbsolutePath()), project.testClassDirectories());
        assertFalse(Files.exists(sentinel));
        assertThrows(UnsupportedOperationException.class, () -> project.mainClassDirectories().clear());
    }

    @Test
    void declaredMavenModulesResolveToTheAggregatorRoot() throws IOException {
        Path root = directory("maven-reactor");
        writePom(root, "<packaging>pom</packaging><modules><module>module-a</module></modules>");
        Path module = directory("maven-reactor/module-a/src/main/java");
        writePom(root.resolve("module-a"), "");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(module);

        assertEquals(root.toAbsolutePath(), result.project().orElseThrow().root());
        assertEquals(BuildSystem.MAVEN, result.project().orElseThrow().buildSystem());
        assertEquals(List.of(root.resolve("module-a/target/classes").toAbsolutePath()),
                result.project().orElseThrow().mainClassDirectories());
        assertEquals(List.of(root.resolve("module-a/target/test-classes").toAbsolutePath()),
                result.project().orElseThrow().testClassDirectories());
    }

    @Test
    void unrelatedNestedMavenProjectsAreReportedAsAmbiguous() throws IOException {
        Path root = directory("ambiguous-maven");
        writePom(root, "");
        Path nestedRoot = directory("ambiguous-maven/independent");
        writePom(nestedRoot, "");
        Path start = directory("ambiguous-maven/independent/src");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(start);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.AMBIGUOUS_PROJECT_ROOTS));
        assertTrue(result.diagnostics().stream()
                .flatMap(diagnostic -> diagnostic.context().values().stream())
                .anyMatch(value -> value.contains(root.toAbsolutePath().toString())
                        && value.contains(nestedRoot.toAbsolutePath().toString())));
    }

    @Test
    void mixedMavenAndGradleMetadataIsNeverGuessed() throws IOException {
        Path root = directory("mixed");
        writePom(root, "");
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.AMBIGUOUS_BUILD_METADATA));
    }

    @Test
    void staticGradleIncludesResolveToTheSettingsRoot() throws IOException {
        Path root = directory("gradle-multi");
        Files.writeString(root.resolve("settings.gradle.kts"), "include(\":app\", \":lib\")");
        Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }");
        Path app = directory("gradle-multi/app/src/main/java");
        Files.writeString(root.resolve("app/build.gradle.kts"), "plugins { java }");
        directory("gradle-multi/lib");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(app);

        DiscoveredProject project = result.project().orElseThrow();
        assertEquals(root.toAbsolutePath(), project.root());
        assertEquals(BuildSystem.GRADLE, project.buildSystem());
        assertEquals(List.of(
                root.resolve("app/build/classes/java/main").toAbsolutePath(),
                root.resolve("build/classes/java/main").toAbsolutePath()),
                project.mainClassDirectories());
        assertEquals(List.of(
                root.resolve("app/build.gradle.kts").toAbsolutePath(),
                root.resolve("build.gradle.kts").toAbsolutePath(),
                root.resolve("settings.gradle.kts").toAbsolutePath()), project.metadataFiles());
    }

    @Test
    void commentedGradleIncludesDoNotResolveNestedProjects() throws IOException {
        Path root = directory("gradle-comment");
        Files.writeString(root.resolve("settings.gradle"), """
                // include ':app'
                /* include(\":app\") */
                if (providers.gradleProperty("enableApp").isPresent()) {
                    include(":app")
                }
                rootProject.name = 'sample'
                """);
        Path app = directory("gradle-comment/app/src");
        Files.writeString(root.resolve("app/build.gradle"), "plugins { id 'java' }");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(app);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.AMBIGUOUS_PROJECT_ROOTS));
    }

    @Test
    void duplicateGradleMetadataVariantsAreAmbiguous() throws IOException {
        Path root = directory("gradle-variants");
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'one'");
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"two\"");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.AMBIGUOUS_BUILD_METADATA));
    }

    @Test
    void customGradleOutputsAreReportedInsteadOfGuessed() throws IOException {
        Path root = directory("gradle-custom-output");
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"");
        Files.writeString(root.resolve("build.gradle.kts"), """
                tasks.compileJava {
                    destinationDirectory.set(layout.buildDirectory.dir("custom-classes"))
                }
                """);

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        assertTrue(result.found());
        assertTrue(result.project().orElseThrow().mainClassDirectories().isEmpty());
        assertTrue(result.project().orElseThrow().testClassDirectories().isEmpty());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.DYNAMIC_METADATA));
    }

    @Test
    void dynamicAndEscapingMavenOutputsProduceDiagnosticsInsteadOfPaths() throws IOException {
        Path root = directory("unsafe-output");
        writePom(root, """
                <build>
                  <outputDirectory>../outside</outputDirectory>
                  <testOutputDirectory>${project.build.directory}/test-classes</testOutputDirectory>
                </build>
                """);

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        assertTrue(result.found());
        assertTrue(result.project().orElseThrow().mainClassDirectories().isEmpty());
        assertTrue(result.project().orElseThrow().testClassDirectories().isEmpty());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.OUTPUT_OUTSIDE_ROOT));
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.DYNAMIC_METADATA));
    }

    @Test
    void oversizedMetadataFailsClosedBeforeParsing() throws IOException {
        Path root = directory("oversized");
        Files.write(root.resolve("pom.xml"), new byte[ProjectDiscovery.MAX_METADATA_BYTES + 1]);

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.METADATA_TOO_LARGE));
    }

    @Test
    void doctypesAndExternalEntitiesAreRejected() throws IOException {
        Path root = directory("xxe");
        Path secret = temporaryDirectory.resolve("secret.txt");
        Files.writeString(secret, "must-not-be-read");
        String pom = """
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY xxe SYSTEM "%s">]>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <build><outputDirectory>&xxe;</outputDirectory></build>
                </project>
                """.formatted(secret.toUri());
        Files.writeString(root.resolve("pom.xml"), pom);

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.MALFORMED_METADATA));
    }

    @Test
    void symlinkedMetadataIsNotFollowedWhenThePlatformSupportsSymlinks() throws IOException {
        Path external = temporaryDirectory.resolve("external-pom.xml");
        Files.writeString(external, pom(""));
        Path root = directory("symlink-marker");
        try {
            Files.createSymbolicLink(root.resolve("pom.xml"), external);
        } catch (IOException | UnsupportedOperationException | SecurityException error) {
            Assumptions.abort("Symbolic links are unavailable: " + error.getClass().getSimpleName());
        }

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.NO_PROJECT_FOUND));
    }

    @Test
    void explicitRootsWithoutBuildMetadataRemainExplicitAndEmpty() throws IOException {
        Path root = directory("plain-explicit");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(root,
                ProjectDiscoveryOptions.builder().explicitRoot(root).build());

        DiscoveredProject project = result.project().orElseThrow();
        assertEquals(BuildSystem.EXPLICIT, project.buildSystem());
        assertTrue(project.mainClassDirectories().isEmpty());
        assertTrue(project.metadataFiles().isEmpty());
    }

    @Test
    void missingStartsAndInvalidBoundsFailPredictably() {
        Path missing = temporaryDirectory.resolve("missing");

        ProjectDiscoveryResult result = ProjectDiscovery.discover(missing);

        assertFalse(result.found());
        assertTrue(hasDiagnostic(result, DiscoveryDiagnosticCode.INVALID_START));
        assertThrows(IllegalArgumentException.class,
                () -> ProjectDiscoveryOptions.builder().maxAncestorDepth(-1));
        assertThrows(IllegalArgumentException.class,
                () -> ProjectDiscoveryOptions.builder().maxAncestorDepth(65));
        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
    }

    private Path directory(String relative) throws IOException {
        return Files.createDirectories(temporaryDirectory.resolve(relative)).toAbsolutePath();
    }

    private static void writePom(Path root, String body) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), pom(body), StandardCharsets.UTF_8);
    }

    private static String pom(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>sample</artifactId>
                  <version>1</version>
                  %s
                </project>
                """.formatted(body);
    }

    private static boolean hasDiagnostic(
            ProjectDiscoveryResult result, DiscoveryDiagnosticCode code) {
        return result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == code);
    }
}
