package dev.archunitjava.model;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileOrigin;
import java.util.Objects;
import dev.archunitjava.graph.LocationId;
import java.util.Locale;

/** Safe class-resource provenance that never contains a machine-specific absolute path. */
public record ClassResourceLocation(
        ClassFileInput.Kind kind, String container, String entry, int precedence)
        implements Comparable<ClassResourceLocation> {
    public ClassResourceLocation {
        Objects.requireNonNull(kind, "kind");
        container = requireSafeSegment(container, "container");
        if (entry == null
                || entry.isBlank()
                || entry.startsWith("/")
                || entry.contains("\\")
                || entry.contains("/../")
                || entry.startsWith("../")
                || entry.endsWith("/..")) {
            throw new IllegalArgumentException("entry must be a safe relative resource name");
        }
        if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
    }

    public static ClassResourceLocation from(ClassFileOrigin origin, int precedence) {
        Objects.requireNonNull(origin, "origin");
        String container = safeLastSegment(origin.input(), precedence);
        return new ClassResourceLocation(origin.kind(), container, origin.entry(), precedence);
    }

    /** Stable logical location suitable for graph evidence without embedding an absolute path. */
    public LocationId locationId() {
        return LocationId.ofResourcePath(
                kind.name().toLowerCase(Locale.ROOT)
                        + "/" + precedence
                        + "/" + container
                        + "/" + entry);
    }

    @Override
    public int compareTo(ClassResourceLocation other) {
        int result = Integer.compare(precedence, other.precedence);
        if (result != 0) return result;
        result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = container.compareTo(other.container);
        return result != 0 ? result : entry.compareTo(other.entry);
    }

    private static String safeLastSegment(String input, int precedence) {
        String normalized = Objects.requireNonNull(input, "input").replace('\\', '/');
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        int separator = normalized.lastIndexOf('/');
        String candidate = separator < 0 ? normalized : normalized.substring(separator + 1);
        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) {
            return "input-" + precedence;
        }
        return requireSafeSegment(candidate, "container");
    }

    private static String requireSafeSegment(String value, String name) {
        if (value == null
                || value.isBlank()
                || value.contains("/")
                || value.contains("\\")
                || value.equals(".")
                || value.equals("..")
                || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must be a safe path segment");
        }
        return value;
    }
}
