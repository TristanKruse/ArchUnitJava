package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImporterSecurityLimitsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void boundsArchiveFileBytesCompressionRatioAndUncompressedBytes() throws IOException {
        Path compressed = jar("compressed.jar", Map.of("Bomb.class", new byte[32 * 1024]));
        InputEnumerationResult archiveBytes = enumerate(compressed, limits(
                16, 1024 * 1024, 1000, 20));
        InputEnumerationResult ratio = enumerate(compressed, limits(
                1024 * 1024, 1024 * 1024, 2, 20));
        Path total = jar("total.jar", Map.of(
                "A.class", new byte[] {1, 2, 3},
                "B.class", new byte[] {4, 5, 6}));
        InputEnumerationResult uncompressed = enumerate(total, limits(
                1024 * 1024, 4, 1000, 20));

        assertEquals(List.of(InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED), codes(archiveBytes));
        assertEquals(List.of(InputDiagnosticCode.ARCHIVE_RESOURCE_REJECTED), codes(ratio));
        assertEquals("compression-ratio", ratio.diagnostics().getFirst().context().get("reason"));
        assertEquals(List.of("A.class"),
                uncompressed.resources().stream().map(ClassFileResource::name).toList());
        assertEquals(List.of(InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED), codes(uncompressed));
    }

    @Test
    void nestedArchivesAreNeverTraversedAndDiagnosticVolumeIsBounded() throws IOException {
        Path nested = jar("nested.jar", Map.of(
                "a.jar", new byte[] {1},
                "b.zip", new byte[] {2},
                "c.jar", new byte[] {3}));
        InputEnumerationResult nestedResult = new ClassFileInputEnumerator(
                        limits(1024 * 1024, 1024 * 1024, 1000, 20))
                .enumerate(List.of(ClassFileInput.jar(nested)));
        Path hostile = jar("diagnostics.jar", Map.of(
                "../Escape.class", new byte[] {4},
                "/Root.class", new byte[] {5},
                "windows\\Alias.class", new byte[] {6},
                "../Second.class", new byte[] {7}));
        InputEnumerationOptions options = limits(1024 * 1024, 1024 * 1024, 1000, 3);

        InputEnumerationResult result = new ClassFileInputEnumerator(options)
                .enumerate(List.of(ClassFileInput.jar(hostile)));

        assertTrue(nestedResult.resources().isEmpty());
        assertTrue(nestedResult.diagnostics().stream()
                .allMatch(value -> value.code() == InputDiagnosticCode.NESTED_ARCHIVE_REJECTED));
        assertTrue(result.resources().isEmpty());
        assertEquals(3, result.diagnostics().size());
        assertTrue(codes(result).contains(InputDiagnosticCode.DIAGNOSTIC_LIMIT_REACHED));
    }

    @Test
    void malformedUtf8IgnoreDataAndOverlongNamesFailWithTypedDiagnostics() throws IOException {
        String overlong = "x".repeat(40) + ".class";
        Path jar = jar("hostile.jar", Map.of(
                ".archignore", new byte[] {(byte) 0xc3, 0x28},
                overlong, new byte[] {1},
                "Safe.class", new byte[] {2}));
        InputEnumerationOptions options = new InputEnumerationOptions(
                8, 8, 8, 32, 32,
                1024 * 1024, 1024 * 1024, 1000, 0, 24, 20);

        InputEnumerationResult result = new ClassFileInputEnumerator(options, ImportOptions.defaults())
                .enumerate(List.of(ClassFileInput.jar(jar)));

        assertEquals(List.of("Safe.class"),
                result.resources().stream().map(ClassFileResource::name).toList());
        assertTrue(codes(result).contains(InputDiagnosticCode.INVALID_IGNORE_RULE));
        assertTrue(codes(result).contains(InputDiagnosticCode.INVALID_RESOURCE_NAME));
    }

    @Test
    void classSizeAndParserDiagnosticVolumeRemainIndependentBounds() throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));
        for (int index = 0; index < 5; index++) {
            Files.write(classes.resolve("Broken" + index + ".class"), new byte[] {1, 2, 3});
        }
        Files.write(classes.resolve("Large.class"), new byte[32]);
        List<ClassFileResource> resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(classes))).resources();

        ClassFileReadResult result = new ClassFileReader(new ClassFileReaderOptions(8, 3))
                .readAll(resources);

        assertTrue(result.classes().isEmpty());
        assertEquals(3, result.diagnostics().size());
        assertTrue(result.diagnostics().stream()
                .anyMatch(value -> value.code() == ClassFileDiagnosticCode.DIAGNOSTIC_LIMIT_REACHED));
    }

    @Test
    void poisonedCachePayloadIsRejectedBeforeBytesAreReturned() throws IOException {
        SafeAnalysisCache cache = new SafeAnalysisCache(temporaryDirectory.resolve("cache"), 1024);
        AnalysisCacheKey key = AnalysisCacheKey.create(
                List.of(CacheInputFingerprint.of("input", new byte[] {1})),
                25, "options", "parser", "library", 1);
        cache.loadOrCompute(key, () -> new byte[] {1, 2, 3});
        byte[] envelope = Files.readAllBytes(cache.entryPath(key));
        envelope[envelope.length - 1] ^= 1;
        Files.write(cache.entryPath(key), envelope);

        AnalysisCacheResult result = cache.loadOrCompute(key, () -> new byte[] {9});

        assertEquals(AnalysisCacheStatus.CORRUPT_REPLACED, result.status());
        assertTrue(Arrays.equals(new byte[] {9}, result.payload()));
    }

    private InputEnumerationResult enumerate(Path jar, InputEnumerationOptions options) {
        return new ClassFileInputEnumerator(options)
                .enumerate(List.of(ClassFileInput.jar(jar)));
    }

    private static InputEnumerationOptions limits(
            long archiveBytes,
            long uncompressedBytes,
            int ratio,
            int diagnostics) {
        return new InputEnumerationOptions(
                8, 100, 8, 100, 100,
                archiveBytes, uncompressedBytes, ratio, 0, 4096, diagnostics);
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

    private static List<InputDiagnosticCode> codes(InputEnumerationResult result) {
        return result.diagnostics().stream().map(InputDiagnostic::code).toList();
    }
}
