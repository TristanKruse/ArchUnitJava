package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.model.DeclarationDependencyExtractor;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractionCorpusSnapshotTest {
    private static final Path CORPUS = Path.of("test-fixtures", "extraction").toAbsolutePath().normalize();

    @TempDir Path temporaryDirectory;

    @Test
    void checkedInCorpusLocksJava8Through25ModelsAndEdges() throws IOException {
        List<String> actual = new ArrayList<>();
        String java25Detail = null;
        for (int release = 8; release <= 25; release++) {
            TypeModelResult result = importFixture(
                    "releases/java-" + release + ".hex", "corpus/JavacFixture.class");
            assertTrue(result.classFileDiagnostics().isEmpty());
            String detail = typeDetail(result.types().getFirst());
            actual.add("release:" + release
                    + "|major:" + result.types().getFirst().classFileVersion().major()
                    + "|model-edges-sha256:" + sha256(detail));
            if (release == 25) java25Detail = detail;
        }
        actual.add("detail-java25|" + java25Detail);
        actual.add("module|" + moduleDetail(importFixture(
                "constructs/module-info.hex", "module-info.class")));
        actual.add("record|" + typeDetail(importFixture(
                "constructs/record.hex", "corpus/ModernFixture.class").types().getFirst()));
        actual.add("sealed|" + typeDetail(importFixture(
                "constructs/sealed.hex", "corpus/Shape.class").types().getFirst()));
        TypeModelResult malformed = importFixture(
                "malformed/truncated.hex", "broken/Truncated.class");
        actual.add("malformed|" + malformed.classFileDiagnostics().stream()
                .map(value -> value.code().name()).toList());

        List<String> expected = Files.readAllLines(
                        CORPUS.resolve("snapshots/model-and-edges.snapshot"), StandardCharsets.UTF_8)
                .stream().filter(line -> !line.startsWith("#") && !line.isBlank()).toList();
        assertEquals(expected, actual);
    }

    @Test
    void generationSourcesAndScriptStayOutsideProductAnalysis() {
        assertTrue(Files.isRegularFile(CORPUS.resolve("sources/java8/corpus/JavacFixture.java")));
        assertTrue(Files.isRegularFile(CORPUS.resolve("sources/java9/module-info.java")));
        assertTrue(Files.isRegularFile(CORPUS.resolve("sources/java17/corpus/ModernFixture.java")));
        assertTrue(Files.isRegularFile(Path.of("scripts", "generate_extraction_corpus.ps1")));
        assertEquals(18, countFiles(CORPUS.resolve("generated/releases"), ".hex"));
    }

    private TypeModelResult importFixture(String relativeHex, String resourceName) throws IOException {
        String hex = Files.readString(CORPUS.resolve("generated").resolve(relativeHex), StandardCharsets.UTF_8)
                .strip();
        if (!hex.matches("(?:[0-9a-f]{2})+")) throw new IOException("Invalid fixture hex: " + relativeHex);
        byte[] bytes = HexFormat.of().parseHex(hex);
        Path root = Files.createTempDirectory(temporaryDirectory, "fixture-");
        Path file = root.resolve(resourceName);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        ClassFileResource resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(root)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource));
    }

    private static String typeDetail(JavaType type) {
        StringBuilder result = new StringBuilder()
                .append("type=").append(type.binaryName())
                .append(";kind=").append(type.kind())
                .append(";major=").append(type.classFileVersion().major())
                .append(";annotations=").append(type.annotations().stream()
                        .map(value -> value.annotation().type().binaryName()).toList())
                .append(";record-components=").append(type.recordComponents().stream()
                        .map(value -> value.name() + value.descriptor()).toList())
                .append(";permitted=").append(type.permittedSubclasses().stream()
                        .map(value -> value.binaryName()).toList())
                .append(";members=").append(type.declaredMembers().stream()
                        .map(member -> member.name() + member.descriptor() + member.modifiers())
                        .toList());
        var declarations = new DeclarationDependencyExtractor().extract(List.of(type));
        result.append(";declarations=").append(declarations.dependencies().stream()
                .map(value -> value.target().binaryName() + "/" + value.evidenceKind()
                        + "/" + value.sources().stream().map(source -> source.kind().name()).toList())
                .toList());
        result.append(";accesses=").append(type.declaredMembers().stream()
                .flatMap(member -> member.codeAccesses().stream())
                .map(value -> value.caller().name() + "->"
                        + value.target().ownerType().descriptor() + "." + value.target().name()
                        + value.target().descriptor() + "/" + value.kind()
                        + "@" + value.location().bytecodeOffset())
                .toList());
        result.append(";dynamic=").append(type.declaredMembers().stream()
                .flatMap(member -> member.dynamicCallSites().stream())
                .map(value -> value.caller().name() + "->" + value.kind()
                        + "/" + value.bootstrapMethod().ownerType().descriptor()
                        + "." + value.bootstrapMethod().name())
                .toList());
        return result.toString();
    }

    private static String moduleDetail(TypeModelResult result) {
        var module = result.modules().getFirst();
        return "name=" + module.identity().name().orElseThrow()
                + ";flags=" + module.flags()
                + ";requires=" + module.requires()
                + ";exports=" + module.exports()
                + ";opens=" + module.opens()
                + ";uses=" + module.uses()
                + ";provides=" + module.provides();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static long countFiles(Path directory, String suffix) {
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(suffix)).count();
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
