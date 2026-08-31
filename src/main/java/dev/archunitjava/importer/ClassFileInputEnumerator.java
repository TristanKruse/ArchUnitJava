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
    private final ImportOptions importOptions;

    public ClassFileInputEnumerator() {
        this(InputEnumerationOptions.defaults(), ImportOptions.defaults());
    }

    public ClassFileInputEnumerator(InputEnumerationOptions options) {
        this(options, ImportOptions.defaults());
    }

    public ClassFileInputEnumerator(ImportOptions importOptions) {
        this(InputEnumerationOptions.defaults(), importOptions);
    }

    public ClassFileInputEnumerator(
            InputEnumerationOptions options, ImportOptions importOptions) {
        this.options = Objects.requireNonNull(options, "options");
        this.importOptions = Objects.requireNonNull(importOptions, "importOptions");
    }

    public InputEnumerationResult enumerate(List<ClassFileInput> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        List<ClassFileResource> resources = new ArrayList<>();
        List<InputDiagnostic> diagnostics =
                new BoundedInputDiagnostics(options.maximumDiagnostics());
        Set<String> identities = new HashSet<>();
        int retainedInputs = Math.min(inputs.size(), options.maximumInputs());
        for (int precedence = 0; precedence < retainedInputs; precedence++) {
            ClassFileInput input = Objects.requireNonNull(inputs.get(precedence), "input");
            if (!identities.add(input.identity())) {
                diagnostics.add(diagnostic(
                        InputDiagnosticCode.DUPLICATE_INPUT, display(input), "precedence", precedence));
                continue;
            }
            enumerate(input, precedence, resources, diagnostics);
        }
        if (inputs.size() > options.maximumInputs()) {
            diagnostics.add(limit("inputs", "inputs", options.maximumInputs()));
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
        List<ImportResourceRule> importRules = directoryRules(root, diagnostics);
        Set<String> names = new HashSet<>();
        int count = 0;
        for (Path candidate : candidates) {
            if (Files.isSymbolicLink(candidate)) {
                diagnostics.add(diagnostic(InputDiagnosticCode.SYMLINK_SKIPPED, candidate.toString()));
                continue;
            }
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    || !fileNameEndsWith(candidate, ".class")) continue;
            if (++count > options.maximumResourcesPerInput()) {
                diagnostics.add(limit(root.toString(), "class-resources", options.maximumResourcesPerInput()));
                break;
            }
            String name = normalizeEntry(root.relativize(candidate).toString());
            if (!validResourceName(name)) {
                diagnostics.add(diagnostic(InputDiagnosticCode.INVALID_RESOURCE_NAME, candidate.toString()));
                continue;
            }
            if (!included(name, root.toString(), importRules, diagnostics)) continue;
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
        long archiveBytes = safeSize(jar);
        if (archiveBytes < 0 || archiveBytes > options.maximumArchiveBytes()) {
            diagnostics.add(limit(
                    jar.toString(), "archive-bytes", options.maximumArchiveBytes()));
            return;
        }
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<ImportResourceRule> importRules = archiveRules(jar, zip, diagnostics);
            int entries = 0;
            int classes = 0;
            long uncompressedBytes = 0;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++entries > options.maximumArchiveEntries()) {
                    diagnostics.add(limit(jar.toString(), "archive-entries", options.maximumArchiveEntries()));
                    break;
                }
                if (entry.isDirectory()) continue;
                if (isNestedArchive(entry.getName())) {
                    diagnostics.add(new InputDiagnostic(
                            InputDiagnosticCode.NESTED_ARCHIVE_REJECTED,
                            jar.toString(),
                            Map.of(
                                    "entry", entry.getName(),
                                    "maximumDepth", Integer.toString(options.maximumArchiveNestingDepth()),
                                    "reason", "nested-archives-are-not-traversed")));
                    continue;
                }
                if (!entry.getName().endsWith(".class")) continue;
                long size = entry.getSize();
                long compressed = entry.getCompressedSize();
                if (size < 0 || compressed < 0) {
                    diagnostics.add(rejectedArchiveResource(jar, entry, "unknown-size"));
                    continue;
                }
                if (compressionRatioExceeds(size, compressed)) {
                    diagnostics.add(rejectedArchiveResource(jar, entry, "compression-ratio"));
                    continue;
                }
                if (Long.MAX_VALUE - uncompressedBytes < size
                        || uncompressedBytes + size > options.maximumArchiveUncompressedBytes()) {
                    diagnostics.add(limit(
                            jar.toString(),
                            "archive-uncompressed-bytes",
                            options.maximumArchiveUncompressedBytes()));
                    break;
                }
                uncompressedBytes += size;
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
                if (!included(name, jar.toString(), importRules, diagnostics)) continue;
                if (!names.add(name)) {
                    diagnostics.add(diagnostic(InputDiagnosticCode.DUPLICATE_RESOURCE, jar.toString(), "entry", name));
                    continue;
                }
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
                        .filter(path -> fileNameEndsWith(path, ".class"))
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

    private List<ImportResourceRule> directoryRules(
            Path root, List<InputDiagnostic> diagnostics) {
        if (!importOptions.readArchIgnore()) return importOptions.rules();
        Path ignore = root.resolve(".archignore");
        if (!Files.exists(ignore, LinkOption.NOFOLLOW_LINKS)) return importOptions.rules();
        if (Files.isSymbolicLink(ignore)) {
            diagnostics.add(diagnostic(InputDiagnosticCode.SYMLINK_SKIPPED, ignore.toString()));
            return importOptions.rules();
        }
        if (!Files.isRegularFile(ignore, LinkOption.NOFOLLOW_LINKS)) {
            diagnostics.add(diagnostic(
                    InputDiagnosticCode.INVALID_IGNORE_RULE,
                    ignore.toString(),
                    "reason",
                    "not-regular-file"));
            return importOptions.rules();
        }
        try (InputStream input = Files.newInputStream(
                ignore, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(importOptions.maximumIgnoreBytes() + 1);
            return parsedRules(bytes, ignore.toString(), diagnostics);
        } catch (IOException | SecurityException failure) {
            diagnostics.add(diagnostic(
                    InputDiagnosticCode.IO_FAILURE,
                    ignore.toString(),
                    "operation",
                    "archignore-read"));
            return importOptions.rules();
        }
    }

    private List<ImportResourceRule> archiveRules(
            Path jar, ZipFile zip, List<InputDiagnostic> diagnostics) {
        if (!importOptions.readArchIgnore()) return importOptions.rules();
        ZipEntry entry = zip.getEntry(".archignore");
        if (entry == null || entry.isDirectory()) return importOptions.rules();
        String source = jar + "!/.archignore";
        if (entry.getSize() > importOptions.maximumIgnoreBytes()) {
            diagnostics.add(new InputDiagnostic(
                    InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                    source,
                    Map.of("limit", "archignore-bytes:" + importOptions.maximumIgnoreBytes())));
            return importOptions.rules();
        }
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(importOptions.maximumIgnoreBytes() + 1);
            return parsedRules(bytes, source, diagnostics);
        } catch (IOException | SecurityException failure) {
            diagnostics.add(diagnostic(
                    InputDiagnosticCode.IO_FAILURE, source, "operation", "archignore-read"));
            return importOptions.rules();
        }
    }

    private List<ImportResourceRule> parsedRules(
            byte[] bytes, String source, List<InputDiagnostic> diagnostics) {
        if (bytes.length > importOptions.maximumIgnoreBytes()) {
            diagnostics.add(new InputDiagnostic(
                    InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                    source,
                    Map.of("limit", "archignore-bytes:" + importOptions.maximumIgnoreBytes())));
            return importOptions.rules();
        }
        ArchIgnoreRules.ParseResult parsed =
                ArchIgnoreRules.parse(bytes, source, importOptions.maximumIgnoreLines());
        diagnostics.addAll(parsed.diagnostics());
        List<ImportResourceRule> combined = new ArrayList<>(parsed.rules());
        combined.addAll(importOptions.rules());
        return List.copyOf(combined);
    }

    private boolean included(
            String resource,
            String input,
            List<ImportResourceRule> rules,
            List<InputDiagnostic> diagnostics) {
        if (!importOptions.scope().includes(resource)) {
            diagnostics.add(new InputDiagnostic(
                    InputDiagnosticCode.RESOURCE_EXCLUDED,
                    input,
                    Map.of(
                            "entry", resource,
                            "rule", "scope:" + importOptions.scope().name(),
                            "source", "scope")));
            return false;
        }
        ImportResourceRule winner = null;
        ImportRuleAction action = ImportRuleAction.INCLUDE;
        for (ImportResourceRule rule : rules) {
            if (rule.matches(resource)) {
                winner = rule;
                action = rule.action();
            }
        }
        if (action == ImportRuleAction.INCLUDE) return true;
        diagnostics.add(new InputDiagnostic(
                InputDiagnosticCode.RESOURCE_EXCLUDED,
                input,
                Map.of(
                        "entry", resource,
                        "line", Integer.toString(winner.line()),
                        "rule", winner.description(),
                        "source", winner.source())));
        return false;
    }

    private static boolean isJar(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) return false;
        String name = fileName.toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    private static boolean fileNameEndsWith(Path path, String suffix) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().endsWith(suffix);
    }

    private static String normalizeEntry(String value) {
        return value.replace('\\', '/');
    }

    private boolean validResourceName(String name) {
        if (name.length() > options.maximumResourceNameCharacters()
                || name.isBlank() || name.startsWith("/") || name.indexOf('\0') >= 0 || name.contains("\\")) {
            return false;
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }

    private boolean compressionRatioExceeds(long size, long compressedSize) {
        if (size == 0) return false;
        long denominator = Math.max(1, compressedSize);
        return size / denominator > options.maximumCompressionRatio()
                || size / denominator == options.maximumCompressionRatio()
                        && size % denominator != 0;
    }

    private static boolean isNestedArchive(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    private static InputDiagnostic rejectedArchiveResource(
            Path jar, ZipEntry entry, String reason) {
        return new InputDiagnostic(
                InputDiagnosticCode.ARCHIVE_RESOURCE_REJECTED,
                jar.toString(),
                Map.of(
                        "compressedBytes", Long.toString(entry.getCompressedSize()),
                        "entry", entry.getName(),
                        "reason", reason,
                        "uncompressedBytes", Long.toString(entry.getSize())));
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

    private static InputDiagnostic limit(String input, String kind, long maximum) {
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
