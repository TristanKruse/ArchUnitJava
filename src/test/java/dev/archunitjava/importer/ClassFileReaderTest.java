package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassFileReaderTest {
    @Test
    void parsesThroughAValueOnlyBoundary() {
        byte[] bytes = classBytes("example.Safe", AccessFlag.PUBLIC, AccessFlag.FINAL);

        ClassFileReadResult result = new ClassFileReader().read(resource("example/Safe.class", bytes));

        ParsedClassFile parsed = result.parsedClass().orElseThrow();
        assertEquals("example.Safe", parsed.binaryName());
        assertEquals(ClassFile.latestMajorVersion(), parsed.majorVersion());
        assertTrue((parsed.accessFlags() & ClassFile.ACC_PUBLIC) != 0);
        assertTrue((parsed.accessFlags() & ClassFile.ACC_FINAL) != 0);
        assertTrue(result.diagnostics().isEmpty());
        assertFalse(parsed.getClass().getName().startsWith("java.lang.classfile"));
    }

    @Test
    void doesNotConsultTheThreadContextClassLoader() {
        byte[] bytes = classBytes("target.HasStaticInitializer", AccessFlag.PUBLIC);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) {
                throw new AssertionError("Class loading is forbidden: " + name);
            }
        });
        try {
            ClassFileReadResult result = new ClassFileReader()
                    .read(resource("target/HasStaticInitializer.class", bytes));
            assertTrue(result.parsedClass().isPresent());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void reportsMalformedHeadersWithResourceAndTraversalContext() {
        ClassFileReadResult result = new ClassFileReader()
                .read(resource("broken/Short.class", new byte[] {1, 2, 3}));

        ClassFileDiagnostic diagnostic = result.diagnostics().getFirst();
        assertEquals(ClassFileDiagnosticCode.MALFORMED_CLASS_FILE, diagnostic.code());
        assertEquals("broken/Short.class", diagnostic.resourceName());
        assertEquals(ClassFileTraversalPhase.PARSE_HEADER, diagnostic.phase());
        assertEquals("truncated-header", diagnostic.context().get("reason"));
        assertTrue(result.classes().isEmpty());
    }

    @Test
    void unsupportedVersionsRemainDistinctFromMalformedFiles() {
        byte[] bytes = classBytes("future.Type", AccessFlag.PUBLIC);
        int futureVersion = ClassFile.latestMajorVersion() + 1;
        bytes[6] = (byte) (futureVersion >>> 8);
        bytes[7] = (byte) futureVersion;

        ClassFileReadResult result = new ClassFileReader().read(resource("future/Type.class", bytes));

        ClassFileDiagnostic diagnostic = result.diagnostics().getFirst();
        assertEquals(ClassFileDiagnosticCode.UNSUPPORTED_CLASS_VERSION, diagnostic.code());
        assertEquals(String.valueOf(futureVersion), diagnostic.context().get("major"));
    }

    @Test
    void catchesBackendFailuresAtTheirLazyTraversalPhase() {
        byte[] bytes = classBytes("broken.Lazy", AccessFlag.PUBLIC);
        ClassFileParserBackend backend = (ignored, observer) -> {
            observer.phase(ClassFileTraversalPhase.TRAVERSE_MODEL);
            throw new IllegalArgumentException("lazy failure");
        };

        ClassFileReadResult result = new ClassFileReader(ClassFileReaderOptions.defaults(), backend)
                .read(resource("broken/Lazy.class", bytes));

        ClassFileDiagnostic diagnostic = result.diagnostics().getFirst();
        assertEquals(ClassFileTraversalPhase.TRAVERSE_MODEL, diagnostic.phase());
        assertEquals(IllegalArgumentException.class.getName(), diagnostic.context().get("failureType"));
    }

    @Test
    void boundsBytesEvenWhenTheContainerSizeIsUnknown() {
        byte[] bytes = classBytes("large.Type", AccessFlag.PUBLIC);
        ClassFileResource resource = new ClassFileResource(
                "large/Type.class", origin("large/Type.class"), 0, -1,
                () -> new ByteArrayInputStream(bytes));

        ClassFileReadResult result = new ClassFileReader(new ClassFileReaderOptions(8)).read(resource);

        assertEquals(ClassFileDiagnosticCode.RESOURCE_TOO_LARGE, result.diagnostics().getFirst().code());
        assertTrue(result.classes().isEmpty());
    }

    @Test
    void catchesResourceIoFailuresWithoutLeakingExceptions() {
        ClassFileResource resource = new ClassFileResource(
                "gone/Type.class", origin("gone/Type.class"), 0, -1,
                () -> { throw new IOException("gone"); });

        ClassFileReadResult result = new ClassFileReader().read(resource);

        assertEquals(ClassFileDiagnosticCode.IO_FAILURE, result.diagnostics().getFirst().code());
        assertEquals(ClassFileTraversalPhase.READ_BYTES, result.diagnostics().getFirst().phase());
    }

    @Test
    void batchResultsAreDeterministicAndImmutable() {
        ClassFileResource second = resource("z/Z.class", classBytes("z.Z", AccessFlag.PUBLIC));
        ClassFileResource first = resource("a/A.class", classBytes("a.A", AccessFlag.PUBLIC));

        ClassFileReadResult result = new ClassFileReader().readAll(List.of(second, first));

        assertEquals(List.of("a.A", "z.Z"),
                result.classes().stream().map(ParsedClassFile::binaryName).toList());
        assertTrue(result.diagnostics().isEmpty());
        try {
            result.classes().clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("classes must be immutable");
    }

    private static byte[] classBytes(String binaryName, AccessFlag... flags) {
        return ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder.withFlags(flags));
    }

    private static ClassFileResource resource(String name, byte[] bytes) {
        return new ClassFileResource(
                name, origin(name), 0, bytes.length, () -> new ByteArrayInputStream(bytes));
    }

    private static ClassFileOrigin origin(String name) {
        return new ClassFileOrigin(ClassFileInput.Kind.DIRECTORY, "test-classes", name);
    }
}
