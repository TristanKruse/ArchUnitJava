package dev.archunitjava.importer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** A class-file resource discovered at a caller-approved input. */
public final class ClassFileResource implements Comparable<ClassFileResource> {
    @FunctionalInterface
    interface ByteSource {
        InputStream open() throws IOException;
    }

    private final String name;
    private final ClassFileOrigin origin;
    private final int precedence;
    private final long declaredSize;
    private final ByteSource byteSource;

    ClassFileResource(
            String name,
            ClassFileOrigin origin,
            int precedence,
            long declaredSize,
            ByteSource byteSource) {
        if (name == null || name.isBlank() || !name.endsWith(".class")) {
            throw new IllegalArgumentException("name must identify a .class resource");
        }
        if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
        if (declaredSize < -1) throw new IllegalArgumentException("declaredSize must be -1 or greater");
        this.name = name;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.precedence = precedence;
        this.declaredSize = declaredSize;
        this.byteSource = Objects.requireNonNull(byteSource, "byteSource");
    }

    public String name() {
        return name;
    }

    public ClassFileOrigin origin() {
        return origin;
    }

    public int precedence() {
        return precedence;
    }

    /** The container-declared size, or {@code -1} when it is unavailable. */
    public long declaredSize() {
        return declaredSize;
    }

    byte[] readBytes(int maximumBytes) throws IOException, ResourceTooLargeException {
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        if (declaredSize > maximumBytes) {
            throw new ResourceTooLargeException(maximumBytes, declaredSize);
        }
        try (InputStream input = byteSource.open();
                ByteArrayOutputStream output = new ByteArrayOutputStream(
                        declaredSize >= 0 ? (int) Math.min(declaredSize, maximumBytes) : 8192)) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer, 0, Math.min(buffer.length, maximumBytes + 1 - total)))
                    != -1) {
                total += read;
                if (total > maximumBytes) {
                    throw new ResourceTooLargeException(maximumBytes, total);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    @Override
    public int compareTo(ClassFileResource other) {
        int result = Integer.compare(precedence, other.precedence);
        if (result != 0) return result;
        result = name.compareTo(other.name);
        return result != 0 ? result : origin.compareTo(other.origin);
    }

    static final class ResourceTooLargeException extends IOException {
        private final int maximumBytes;
        private final long observedBytes;

        ResourceTooLargeException(int maximumBytes, long observedBytes) {
            super("Class resource exceeds the configured byte limit");
            this.maximumBytes = maximumBytes;
            this.observedBytes = observedBytes;
        }

        int maximumBytes() {
            return maximumBytes;
        }

        long observedBytes() {
            return observedBytes;
        }
    }
}
