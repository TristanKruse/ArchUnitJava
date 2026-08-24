package dev.archunitjava.importer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Enumerates class resources without loading target classes or following symbolic links. */
public final class ClassFileInputEnumerator {
    private final InputEnumerationOptions options;

    public ClassFileInputEnumerator() {
        this(InputEnumerationOptions.defaults());
    }

    public ClassFileInputEnumerator(InputEnumerationOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public InputEnumerationResult enumerate(List<ClassFileInput> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        List<ClassFileResource> resources = new ArrayList<>();
        List<InputDiagnostic> diagnostics = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        for (int precedence = 0; precedence < inputs.size(); precedence++) {
            ClassFileInput input = Objects.requireNonNull(inputs.get(precedence), "input");
            if (!identities.add(input.identity())) {
                diagnostics.add(diagnostic(
                        InputDiagnosticCode.DUPLICATE_INPUT, display(input), "precedence", precedence));
                continue;
            }
            enumerate(input, precedence, resources, diagnostics);
        }
        return new InputEnumerationResult(resources, diagnostics);
    }

    private void enumerate(
            ClassFileInput input,
            int precedence,
            List<ClassFileResource> resources,
            List<InputDiagnostic> diagnostics) {
        if (input.kind() == ClassFileInput.Kind.JRT_MODULE) {
            enumerateJrt(input, precedence, resources, diagnostics);
            return;
        }
        Path path = input.path().orElseThrow();
        String display = path.toString();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            diagnostics.add(diagnostic(InputDiagnosticCode.MISSING_INPUT, display));
            return;
        }
        if (Files.isSymbolicLink(path)) {
            diagnostics.add(diagnostic(InputDiagnosticCode.SYMLINK_SKIPPED, display));
            return;
        }
        if (!Files.isReadable(path)) {
            diagnostics.add(diagnostic(InputDiagnosticCode.UNREADABLE_INPUT, display));
            return;
        }
        ClassFileInput.Kind actualKind = input.kind();
        if (actualKind == ClassFileInput.Kind.AUTO) {
            actualKind = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    ? ClassFileInput.Kind.DIRECTORY
                    : isJar(path) ? ClassFileInput.Kind.JAR : ClassFileInput.Kind.AUTO;
        }
        switch (actualKind) {
            case DIRECTORY -> enumerateDirectory(path, precedence, resources, diagnostics);
            case JAR -> enumerateJar(path, precedence, resources, diagnostics);
            default -> diagnostics.add(diagnostic(InputDiagnosticCode.UNSUPPORTED_INPUT, display));
        }
    }

    private void enumerateDirectory(
            Path root,
            int precedence,
            List<ClassFileResource> resources,
            List<InputDiagnostic> diagnostics) {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            diagnostics.add(diagnostic(InputDiagnosticCode.UNSUPPORTED_INPUT, root.toString()));
            return;
        }
        List<Path> candidates;
        try (var paths = Files.walk(root, options.maximumDirectoryDepth())) {
            candidates = paths.limit((long) options.maximumDirectoryEntries() + 1).toList();
        } catch (IOException | SecurityException failure) {
            diagnostics.add(diagnostic(
                    InputDiagnosticCode.IO_FAILURE,
                    root.toString(),
                    "operation",
                    "directory-traversal"));
            return;
        }
        if (candidates.size() > options.maximumDirectoryEntries()) {
            diagnostics.add(limit(
                    root.toString(), "directory-entries", options.maximumDirectoryEntries()));
            return;
        }
        candidates = candidates.stream().sorted().toList();
        Set<String> names = new HashSet<>();
        int count = 0;
        for (Path candidate : candidates) {
            if (Files.isSymbolicLink(candidate)) {
                diagnostics.add(diagnostic(InputDiagnosticCode.SYMLINK_SKIPPED, candidate.toString()));
                continue;
            }
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    || !candidate.getFileName().toString().endsWith(".class")) continue;
            if (++count > options.maximumResourcesPerInput()) {
                diagnostics.add(limit(root.toString(), "class-resources", options.maximumResourcesPerInput()));
                break;
            }
            String name = normalizeEntry(root.relativize(candidate).toString());
            if (!validResourceName(name)) {
                diagnostics.add(diagnostic(InputDiagnosticCode.INVALID_RESOURCE_NAME, candidate.toString()));
                continue;
            }
            if (!names.add(name)) {
                diagnostics.add(diagnostic(InputDiagnosticCode.DUPLICATE_RESOURCE, root.toString(), "entry", name));
                continue;
            }
            long size = safeSize(candidate);
            Path approvedFile = candidate;
            resources.add(new ClassFileResource(
                    name,
                    new ClassFileOrigin(ClassFileInput.Kind.DIRECTORY, root.toString(), name),
                    precedence,
                    size,
                    () -> Files.newInputStream(approvedFile, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)));
        }
    }

    private void enumerateJar(
            Path jar,
            int precedence,
            List<ClassFileResource> resources,
            List<InputDiagnostic> diagnostics) {
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || !isJar(jar)) {
            diagnostics.add(diagnostic(InputDiagnosticCode.UNSUPPORTED_INPUT, jar.toString()));
            return;
        }
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            int entries = 0;
            int classes = 0;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++entries > options.maximumArchiveEntries()) {
                    diagnostics.add(limit(jar.toString(), "archive-entries", options.maximumArchiveEntries()));
                    break;
                }
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                if (++classes > options.maximumResourcesPerInput()) {
                    diagnostics.add(limit(jar.toString(), "class-resources", options.maximumResourcesPerInput()));
                    break;
                }
                String archiveName = entry.getName();
                if (!validResourceName(archiveName)) {
                    diagnostics.add(diagnostic(InputDiagnosticCode.INVALID_RESOURCE_NAME, jar.toString(), "entry", entry.getName()));
                    continue;
                }
                String name = normalizeEntry(archiveName);
                if (!names.add(name)) {
                    diagnostics.add(diagnostic(InputDiagnosticCode.DUPLICATE_RESOURCE, jar.toString(), "entry", name));
                    continue;
                }
                long size = entry.getSize();
                resources.add(new ClassFileResource(
                        name,
                        new ClassFileOrigin(ClassFileInput.Kind.JAR, jar.toString(), name),
                        precedence,
                        size,
                        () -> openZipEntry(jar, name)));
            }
        } catch (IOException | SecurityException failure) {
            diagnostics.add(diagnostic(
                    InputDiagnosticCode.IO_FAILURE, jar.toString(), "operation", "archive-traversal"));
        }
    }

    private void enumerateJrt(
            ClassFileInput input,
            int precedence,
            List<ClassFileResource> resources,
            List<InputDiagnostic> diagnostics) {
        String module = input.moduleName().orElseThrow();
        String display = "jrt:/" + module;
        if (!module.matches("[A-Za-z0-9][A-Za-z0-9_.]*")) {
            diagnostics.add(diagnostic(InputDiagnosticCode.UNSUPPORTED_INPUT, display));
            return;
        }
        try {
            FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            Path root = jrt.getPath("/modules", module);
            if (!Files.isDirectory(root)) {
                diagnostics.add(diagnostic(InputDiagnosticCode.MISSING_INPUT, display));
                return;
            }
            List<Path> classes;
            try (var paths = Files.walk(root, options.maximumDirectoryDepth())) {
                List<Path> candidates = paths
                        .limit((long) options.maximumDirectoryEntries() + 1)
                        .toList();
                if (candidates.size() > options.maximumDirectoryEntries()) {
                    diagnostics.add(limit(
                            display, "directory-entries", options.maximumDirectoryEntries()));
                    return;
                }
                classes = candidates.stream()
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".class"))
                        .sorted()
                        .toList();
            }
            int count = 0;
            for (Path path : classes) {
                if (++count > options.maximumResourcesPerInput()) {
                    diagnostics.add(limit(display, "class-resources", options.maximumResourcesPerInput()));
                    break;
                }
                String name = normalizeEntry(root.relativize(path).toString());
                long size = safeSize(path);
                resources.add(new ClassFileResource(
                        name,
                        new ClassFileOrigin(ClassFileInput.Kind.JRT_MODULE, module, name),
                        precedence,
                        size,
                        () -> Files.newInputStream(path, StandardOpenOption.READ)));
            }
        } catch (IOException | RuntimeException failure) {
            diagnostics.add(diagnostic(InputDiagnosticCode.IO_FAILURE, display, "operation", "jrt-traversal"));
        }
    }

    private static InputStream openZipEntry(Path jar, String name) throws IOException {
        ZipFile zip = new ZipFile(jar.toFile());
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            zip.close();
            throw new IOException("Archive entry disappeared");
        }
        InputStream input = zip.getInputStream(entry);
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return input.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                return input.read(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                try (zip; input) {
                    // Both resources close through try-with-resources.
                }
            }
        };
    }

    private static boolean isJar(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static String normalizeEntry(String value) {
        return value.replace('\\', '/');
    }

    private static boolean validResourceName(String name) {
        if (name.isBlank() || name.startsWith("/") || name.indexOf('\0') >= 0 || name.contains("\\")) {
            return false;
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException | SecurityException ignored) {
            return -1;
        }
    }

    private static String display(ClassFileInput input) {
        return input.path().map(Path::toString).orElseGet(() -> "jrt:/" + input.moduleName().orElseThrow());
    }

    private static InputDiagnostic limit(String input, String kind, int maximum) {
        return diagnostic(InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED, input, "limit", kind + ":" + maximum);
    }

    private static InputDiagnostic diagnostic(InputDiagnosticCode code, String input) {
        return new InputDiagnostic(code, input, Map.of());
    }

    private static InputDiagnostic diagnostic(
            InputDiagnosticCode code, String input, String key, Object value) {
        return new InputDiagnostic(code, input, Map.of(key, String.valueOf(value)));
    }
}
