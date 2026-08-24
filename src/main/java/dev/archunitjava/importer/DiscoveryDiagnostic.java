package dev.archunitjava.importer;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Structured project-discovery information, independent from human rendering. */
public record DiscoveryDiagnostic(
        DiscoveryDiagnosticCode code, Path path, Map<String, String> context)
        implements Comparable<DiscoveryDiagnostic> {
    public DiscoveryDiagnostic {
        Objects.requireNonNull(code, "code");
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Objects.requireNonNull(context, "context");
        TreeMap<String, String> sorted = new TreeMap<>();
        context.forEach((key, value) -> sorted.put(requireText(key), requireText(value)));
        context = Collections.unmodifiableMap(sorted);
    }

    @Override
    public int compareTo(DiscoveryDiagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        result = path.toString().compareTo(other.path.toString());
        if (result != 0) return result;
        var left = context.entrySet().iterator();
        var right = other.context.entrySet().iterator();
        while (left.hasNext() && right.hasNext()) {
            var leftEntry = left.next();
            var rightEntry = right.next();
            result = leftEntry.getKey().compareTo(rightEntry.getKey());
            if (result != 0) return result;
            result = leftEntry.getValue().compareTo(rightEntry.getValue());
            if (result != 0) return result;
        }
        return Boolean.compare(left.hasNext(), right.hasNext());
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Diagnostic context must not be blank");
        }
        return value;
    }
}
