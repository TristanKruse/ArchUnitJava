package dev.archunitjava.importer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** A caller-approved source of class-file resources. */
public final class ClassFileInput {
    public enum Kind {
        AUTO,
        DIRECTORY,
        JAR,
        JRT_MODULE
    }

    private final Kind kind;
    private final Path path;
    private final String moduleName;

    private ClassFileInput(Kind kind, Path path, String moduleName) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.path = path == null ? null : path.toAbsolutePath().normalize();
        this.moduleName = moduleName;
    }

    public static ClassFileInput path(Path path) {
        return new ClassFileInput(Kind.AUTO, Objects.requireNonNull(path, "path"), null);
    }

    public static ClassFileInput directory(Path path) {
        return new ClassFileInput(Kind.DIRECTORY, Objects.requireNonNull(path, "path"), null);
    }

    public static ClassFileInput jar(Path path) {
        return new ClassFileInput(Kind.JAR, Objects.requireNonNull(path, "path"), null);
    }

    public static ClassFileInput jrtModule(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            throw new IllegalArgumentException("moduleName must not be blank");
        }
        return new ClassFileInput(Kind.JRT_MODULE, null, moduleName);
    }

    public Kind kind() {
        return kind;
    }

    public Optional<Path> path() {
        return Optional.ofNullable(path);
    }

    public Optional<String> moduleName() {
        return Optional.ofNullable(moduleName);
    }

    String identity() {
        return path != null ? "PATH:" + path : kind + ":" + moduleName;
    }
}
