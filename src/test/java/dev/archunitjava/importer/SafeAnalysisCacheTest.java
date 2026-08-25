package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeAnalysisCacheTest {
    @TempDir Path temporaryDirectory;

    @Test
    void keysCoverBytesTargetOptionsParserLibraryAndSchemaWithoutTimestamps() throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("classes"));
        Path file = classes.resolve("sample/Type.class");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        FileTime unchangedTime = Files.getLastModifiedTime(file);
        ClassFileResource resource = resource(classes);
        ClassPathAssemblyOptions defaults = ClassPathAssemblyOptions.classPathDefaults();
        AnalysisCacheKey first = resourceKey(resource, defaults, "parser-1", "library-1", 1);

        Files.write(file, new byte[] {3, 2, 1});
        Files.setLastModifiedTime(file, unchangedTime);
        AnalysisCacheKey changedBytes = resourceKey(resource, defaults, "parser-1", "library-1", 1);
        AnalysisCacheKey changedTarget = resourceKey(
                resource, defaults.withTargetJavaRelease(defaults.targetJavaRelease() - 1),
                "parser-1", "library-1", 1);
        AnalysisCacheKey changedOptions = resourceKey(
                resource,
                defaults.withImportOptions(ImportOptions.defaults()
                        .withRule(ImportResourceRule.exclude("internal/**"))),
                "parser-1", "library-1", 1);

        assertNotEquals(first, changedBytes);
        assertNotEquals(changedBytes, changedTarget);
        assertNotEquals(changedBytes, changedOptions);
        assertNotEquals(changedBytes, resourceKey(resource, defaults, "parser-2", "library-1", 1));
        assertNotEquals(changedBytes, resourceKey(resource, defaults, "parser-1", "library-2", 1));
        assertNotEquals(changedBytes, resourceKey(resource, defaults, "parser-1", "library-1", 2));
    }

    @Test
    void validatedEntriesHitAndPayloadArraysCannotMutateTheCache() throws IOException {
        SafeAnalysisCache cache = new SafeAnalysisCache(temporaryDirectory.resolve("cache"), 1024);
        AnalysisCacheKey key = key("input", 25, "options", "parser", "library", 1);

        AnalysisCacheResult stored = cache.loadOrCompute(key, () -> new byte[] {1, 2, 3});
        byte[] exposed = stored.payload();
        exposed[0] = 99;
        AnalysisCacheResult hit = cache.loadOrCompute(key, () -> {
            throw new AssertionError("validated hit must not recompute");
        });

        assertEquals(AnalysisCacheStatus.MISS_STORED, stored.status());
        assertEquals(AnalysisCacheStatus.HIT, hit.status());
        assertArrayEquals(new byte[] {1, 2, 3}, hit.payload());
        assertEquals(cache.root(), cache.entryPath(key).getParent());
    }

    @Test
    void partialCorruptAndForeignEntriesFailClosedAndSelfHeal() throws IOException {
        SafeAnalysisCache cache = new SafeAnalysisCache(temporaryDirectory.resolve("healing"), 1024);
        AnalysisCacheKey firstKey = key("first", 25, "options", "parser", "library", 1);
        AnalysisCacheKey secondKey = key("second", 25, "options", "parser", "library", 1);
        cache.loadOrCompute(firstKey, () -> new byte[] {1, 2, 3});

        byte[] complete = Files.readAllBytes(cache.entryPath(firstKey));
        Files.write(cache.entryPath(firstKey), Arrays.copyOf(complete, 12));
        AnalysisCacheResult partial = cache.loadOrCompute(firstKey, () -> new byte[] {4, 5});
        assertEquals(AnalysisCacheStatus.PARTIAL_REPLACED, partial.status());
        assertArrayEquals(new byte[] {4, 5}, partial.payload());

        byte[] healed = Files.readAllBytes(cache.entryPath(firstKey));
        healed[healed.length - 1] ^= 1;
        Files.write(cache.entryPath(firstKey), healed);
        AnalysisCacheResult corrupt = cache.loadOrCompute(firstKey, () -> new byte[] {6});
        assertEquals(AnalysisCacheStatus.CORRUPT_REPLACED, corrupt.status());

        Files.copy(cache.entryPath(firstKey), cache.entryPath(secondKey));
        AnalysisCacheResult foreign = cache.loadOrCompute(secondKey, () -> new byte[] {7});
        assertEquals(AnalysisCacheStatus.FOREIGN_REPLACED, foreign.status());
        assertEquals(AnalysisCacheStatus.HIT,
                cache.loadOrCompute(secondKey, () -> new byte[] {8}).status());
        assertArrayEquals(new byte[] {7}, cache.loadOrCompute(secondKey, () -> new byte[] {8}).payload());
    }

    @Test
    void concurrentLookupsComputeOneEntryAndAllObserveTheSamePayload() throws Exception {
        SafeAnalysisCache cache = new SafeAnalysisCache(temporaryDirectory.resolve("concurrent"), 1024);
        AnalysisCacheKey key = key("shared", 25, "options", "parser", "library", 1);
        AtomicInteger computations = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<AnalysisCacheResult>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.loadOrCompute(key, () -> {
                        computations.incrementAndGet();
                        return new byte[] {9, 8, 7};
                    });
                }));
            }
            start.countDown();
            for (Future<AnalysisCacheResult> future : futures) {
                assertArrayEquals(new byte[] {9, 8, 7}, future.get().payload());
            }
        }
        assertEquals(1, computations.get());
    }

    @Test
    void opaquePayloadsNeverTriggerJavaDeserializationAndBoundsAreEnforced() throws IOException {
        HostilePayload.reads.set(0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new HostilePayload());
        }
        SafeAnalysisCache cache = new SafeAnalysisCache(temporaryDirectory.resolve("opaque"), 4096);
        AnalysisCacheKey key = key("opaque", 25, "options", "parser", "library", 1);

        cache.loadOrCompute(key, bytes::toByteArray);
        cache.loadOrCompute(key, () -> new byte[0]);

        assertEquals(0, HostilePayload.reads.get());
        SafeAnalysisCache bounded = new SafeAnalysisCache(temporaryDirectory.resolve("bounded"), 2);
        assertThrows(IOException.class,
                () -> bounded.loadOrCompute(key, () -> new byte[] {1, 2, 3}));
        assertTrue(!Files.exists(bounded.entryPath(key), java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    private ClassFileResource resource(Path root) {
        return new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(root)))
                .resources().getFirst();
    }

    private static AnalysisCacheKey resourceKey(
            ClassFileResource resource,
            ClassPathAssemblyOptions options,
            String parser,
            String library,
            int schema)
            throws IOException {
        return AnalysisCacheKey.fromResources(
                List.of(resource), options, parser, library, schema, 1024);
    }

    private static AnalysisCacheKey key(
            String content,
            int target,
            String options,
            String parser,
            String library,
            int schema) {
        return AnalysisCacheKey.create(
                List.of(CacheInputFingerprint.of("input", content.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                target,
                options,
                parser,
                library,
                schema);
    }

    private static final class HostilePayload implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private static final AtomicInteger reads = new AtomicInteger();

        @Serial
        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            reads.incrementAndGet();
            input.defaultReadObject();
        }
    }
}
