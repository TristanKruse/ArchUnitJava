package dev.archunitjava.importer;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** A selected project root and its declared or conventional compiled-output layout. */
public record DiscoveredProject(
        Path root,
        BuildSystem buildSystem,
        List<Path> mainClassDirectories,
        List<Path> testClassDirectories,
        List<Path> metadataFiles) {
    private static final Comparator<Path> PATH_ORDER = Comparator.comparing(Path::toString);

    public DiscoveredProject {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(buildSystem, "buildSystem");
        mainClassDirectories = pathsInside(root, mainClassDirectories, "main class directory");
        testClassDirectories = pathsInside(root, testClassDirectories, "test class directory");
        metadataFiles = pathsInside(root, metadataFiles, "metadata file");
    }

    private static List<Path> pathsInside(Path root, List<Path> paths, String description) {
        Objects.requireNonNull(paths, description + " paths");
        TreeSet<Path> normalized = new TreeSet<>(PATH_ORDER);
        for (Path path : paths) {
            Path value = Objects.requireNonNull(path, description).toAbsolutePath().normalize();
            if (!value.startsWith(root)) {
                throw new IllegalArgumentException(description + " escapes project root: " + value);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }
}
