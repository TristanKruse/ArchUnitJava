package dev.archunitjava.importer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Applies explicit Java lookup precedence without loading any target class. */
public final class ClassPathAssembler {
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";

    private final ClassPathAssemblyOptions options;

    public ClassPathAssembler() {
        this(ClassPathAssemblyOptions.classPathDefaults());
    }

    public ClassPathAssembler(ClassPathAssemblyOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public ClassPathAssemblyResult assemble(List<ClassFileInput> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        List<InputDiagnostic> diagnostics = new ArrayList<>();
        List<ClassFileInput> effectiveInputs = options.followManifestClassPath()
                ? expandManifestClassPath(inputs, diagnostics)
                : List.copyOf(inputs);
        InputEnumerationResult enumerated = new ClassFileInputEnumerator(options.enumerationOptions())
                .enumerate(effectiveInputs);
        diagnostics.addAll(enumerated.diagnostics());
        TreeMap<String, List<ClassFileResource>> groups = new TreeMap<>();
        for (ClassFileResource resource : enumerated.resources()) {
            String scope = lookupScope(resource.origin());
            groups.computeIfAbsent(scope + "\0" + resource.name(), ignored -> new ArrayList<>())
                    .add(resource);
        }
        List<SelectedClassResource> selections = groups.values().stream()
                .map(values -> {
                    List<ClassFileResource> ordered = values.stream().sorted().toList();
                    ClassFileResource winner = ordered.getFirst();
                    return new SelectedClassResource(
                            lookupScope(winner.origin()),
                            winner.name(),
                            winner,
                            ordered.subList(1, ordered.size()));
                })
                .toList();
        return new ClassPathAssemblyResult(selections, diagnostics);
    }

    private List<ClassFileInput> expandManifestClassPath(
            List<ClassFileInput> inputs, List<InputDiagnostic> diagnostics) {
        List<ClassFileInput> expanded = new ArrayList<>();
        Set<Path> visitedManifestJars = new HashSet<>();
        int[] followedEntries = {0};
        for (ClassFileInput input : inputs) {
            Objects.requireNonNull(input, "input");
            Path containmentRoot = input.path()
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .map(Path::getParent)
                    .orElse(null);
            expand(input, containmentRoot, 0, expanded, diagnostics, visitedManifestJars, followedEntries);
        }
        return List.copyOf(expanded);
    }

    private void expand(
            ClassFileInput input,
            Path containmentRoot,
            int depth,
            List<ClassFileInput> expanded,
            List<InputDiagnostic> diagnostics,
            Set<Path> visited,
            int[] followedEntries) {
        expanded.add(input);
        Path jar = manifestJar(input);
        if (jar == null || containmentRoot == null) return;
        jar = jar.toAbsolutePath().normalize();
        if (!visited.add(jar)) return;
        if (depth >= options.maximumManifestDepth()) {
            diagnostics.add(limit(jar, "manifest-depth", options.maximumManifestDepth()));
            return;
        }
        for (String entry : manifestClassPath(jar, diagnostics)) {
            if (followedEntries[0] >= options.maximumManifestClassPathEntries()) {
                diagnostics.add(limit(
                        jar,
                        "manifest-class-path-entries",
                        options.maximumManifestClassPathEntries()));
                return;
            }
            followedEntries[0]++;
            Path resolved = containedManifestEntry(jar, containmentRoot, entry);
            if (resolved == null) {
                diagnostics.add(new InputDiagnostic(
                        InputDiagnosticCode.MANIFEST_CLASS_PATH_REJECTED,
                        jar.toString(),
                        Map.of("entry", entry)));
                continue;
            }
            expand(
                    ClassFileInput.jar(resolved),
                    containmentRoot,
                    depth + 1,
                    expanded,
                    diagnostics,
                    visited,
                    followedEntries);
        }
    }

    private List<String> manifestClassPath(Path jar, List<InputDiagnostic> diagnostics) {
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(jar)) {
            return List.of();
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(MANIFEST_NAME);
            if (entry == null) return List.of();
            if (entry.getSize() > options.maximumManifestBytes()) {
                diagnostics.add(limit(jar, "manifest-bytes", options.maximumManifestBytes()));
                return List.of();
            }
            byte[] bytes;
            try (var input = zip.getInputStream(entry)) {
                bytes = input.readNBytes(options.maximumManifestBytes() + 1);
            }
            if (bytes.length > options.maximumManifestBytes()) {
                diagnostics.add(limit(jar, "manifest-bytes", options.maximumManifestBytes()));
                return List.of();
            }
            String value = new Manifest(new ByteArrayInputStream(bytes))
                    .getMainAttributes()
                    .getValue(Attributes.Name.CLASS_PATH);
            return value == null || value.isBlank()
                    ? List.of()
                    : List.of(value.trim().split("\\s+"));
        } catch (IOException | RuntimeException failure) {
            diagnostics.add(new InputDiagnostic(
                    InputDiagnosticCode.IO_FAILURE,
                    jar.toString(),
                    Map.of("operation", "manifest-read")));
            return List.of();
        }
    }

    private static Path containedManifestEntry(Path jar, Path containmentRoot, String entry) {
        if (entry == null || entry.isBlank() || entry.indexOf('\\') >= 0 || entry.indexOf('\0') >= 0) {
            return null;
        }
        try {
            URI uri = new URI(entry);
            if (uri.isAbsolute()
                    || uri.getAuthority() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || uri.getPath() == null
                    || uri.getPath().startsWith("/")) return null;
            Path resolved = jar.getParent().resolve(uri.getPath()).toAbsolutePath().normalize();
            return resolved.startsWith(containmentRoot) ? resolved : null;
        } catch (URISyntaxException | IllegalArgumentException failure) {
            return null;
        }
    }

    private static Path manifestJar(ClassFileInput input) {
        if (input.kind() == ClassFileInput.Kind.JAR) return input.path().orElse(null);
        if (input.kind() != ClassFileInput.Kind.AUTO) return null;
        return input.path()
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return name.endsWith(".jar") || name.endsWith(".zip");
                })
                .orElse(null);
    }

    private String lookupScope(ClassFileOrigin origin) {
        if (options.mode() == ClassPathAssemblyMode.CLASSPATH) return "classpath";
        return "module-path:" + origin.kind() + ":" + origin.input();
    }

    private static InputDiagnostic limit(Path input, String kind, int maximum) {
        return new InputDiagnostic(
                InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                input.toString(),
                Map.of("limit", kind + ":" + maximum));
    }
}
