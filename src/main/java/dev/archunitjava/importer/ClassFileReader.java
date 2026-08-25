package dev.archunitjava.importer;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads untrusted class bytes through a model-neutral JDK Class-File API boundary. */
public final class ClassFileReader {
    private static final int HEADER_BYTES = 8;
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    private final ClassFileReaderOptions options;
    private final ClassFileParserBackend backend;

    public ClassFileReader() {
        this(ClassFileReaderOptions.defaults());
    }

    public ClassFileReader(ClassFileReaderOptions options) {
        this(options, new JdkClassFileParserBackend());
    }

    ClassFileReader(ClassFileReaderOptions options, ClassFileParserBackend backend) {
        this.options = Objects.requireNonNull(options, "options");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public ClassFileReadResult read(ClassFileResource resource) {
        return readAll(List.of(resource));
    }

    public ClassFileReadResult readAll(List<ClassFileResource> resources) {
        Objects.requireNonNull(resources, "resources");
        List<ParsedClassFile> classes = new ArrayList<>();
        List<ClassFileDiagnostic> diagnostics = new ArrayList<>();
        for (ClassFileResource resource : resources) {
            read(Objects.requireNonNull(resource, "resource"), classes, diagnostics);
        }
        return new ClassFileReadResult(classes, diagnostics);
    }

    private void read(
            ClassFileResource resource,
            List<ParsedClassFile> classes,
            List<ClassFileDiagnostic> diagnostics) {
        byte[] bytes;
        try {
            bytes = resource.readBytes(options.maximumClassBytes());
        } catch (ClassFileResource.ResourceTooLargeException failure) {
            diagnostics.add(diagnostic(
                    resource,
                    ClassFileDiagnosticCode.RESOURCE_TOO_LARGE,
                    ClassFileTraversalPhase.READ_BYTES,
                    Map.of(
                            "maximumBytes", String.valueOf(failure.maximumBytes()),
                            "observedBytes", String.valueOf(failure.observedBytes()))));
            return;
        } catch (IOException | SecurityException failure) {
            diagnostics.add(failure(resource, ClassFileDiagnosticCode.IO_FAILURE,
                    ClassFileTraversalPhase.READ_BYTES, failure));
            return;
        }

        ClassFileDiagnostic headerFailure = validateHeader(resource, bytes);
        if (headerFailure != null) {
            diagnostics.add(headerFailure);
            return;
        }

        PhaseTracker tracker = new PhaseTracker();
        try {
            ClassFileParserBackend.ParsedClassHeader header = backend.parse(bytes, tracker::set);
            classes.add(new ParsedClassFile(
                    header.binaryName(),
                    header.accessFlags(),
                    header.majorVersion(),
                    header.minorVersion(),
                    header.moduleDescriptor(),
                    resource.name(),
                    resource.origin(),
                    resource.precedence(),
                    header.superclassBinaryName(),
                    header.interfaceBinaryNames(),
                    header.sourceFile(),
                    header.declaredMembers(),
                    header.annotations(),
                    header.annotationDefaults(),
                    header.genericSignature(),
                    header.recordDeclaration(),
                    header.recordComponents(),
                    header.sealedDeclaration(),
                    header.permittedSubclassBinaryNames(),
                    header.nestingMetadata()));
        } catch (RuntimeException failure) {
            diagnostics.add(failure(resource, ClassFileDiagnosticCode.MALFORMED_CLASS_FILE,
                    tracker.phase, failure));
        }
    }

    private static ClassFileDiagnostic validateHeader(ClassFileResource resource, byte[] bytes) {
        if (bytes.length < HEADER_BYTES) {
            return diagnostic(resource, ClassFileDiagnosticCode.MALFORMED_CLASS_FILE,
                    ClassFileTraversalPhase.PARSE_HEADER,
                    Map.of("reason", "truncated-header", "bytes", String.valueOf(bytes.length)));
        }
        int magic = ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
        if (magic != CLASS_FILE_MAGIC) {
            return diagnostic(resource, ClassFileDiagnosticCode.MALFORMED_CLASS_FILE,
                    ClassFileTraversalPhase.PARSE_HEADER, Map.of("reason", "invalid-magic"));
        }
        int minor = unsignedShort(bytes, 4);
        int major = unsignedShort(bytes, 6);
        if (major < ClassFile.JAVA_1_VERSION || major > ClassFile.latestMajorVersion()) {
            return diagnostic(resource, ClassFileDiagnosticCode.UNSUPPORTED_CLASS_VERSION,
                    ClassFileTraversalPhase.PARSE_HEADER,
                    Map.of(
                            "major", String.valueOf(major),
                            "minor", String.valueOf(minor),
                            "latestMajor", String.valueOf(ClassFile.latestMajorVersion())));
        }
        return null;
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static ClassFileDiagnostic failure(
            ClassFileResource resource,
            ClassFileDiagnosticCode code,
            ClassFileTraversalPhase phase,
            Exception failure) {
        return diagnostic(resource, code, phase, Map.of("failureType", failure.getClass().getName()));
    }

    private static ClassFileDiagnostic diagnostic(
            ClassFileResource resource,
            ClassFileDiagnosticCode code,
            ClassFileTraversalPhase phase,
            Map<String, String> context) {
        return new ClassFileDiagnostic(code, resource.name(), resource.origin(), phase, context);
    }

    private static final class PhaseTracker {
        private ClassFileTraversalPhase phase = ClassFileTraversalPhase.PARSE_MODEL;

        void set(ClassFileTraversalPhase value) {
            phase = Objects.requireNonNull(value, "phase");
        }
    }
}
