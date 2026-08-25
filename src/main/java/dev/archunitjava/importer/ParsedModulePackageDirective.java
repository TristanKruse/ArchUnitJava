package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** One exports or opens directive, including an empty or qualified target list. */
public record ParsedModulePackageDirective(
        Kind kind, String packageName, int flags, List<String> targetModules)
        implements Comparable<ParsedModulePackageDirective> {
    public enum Kind {
        EXPORTS,
        OPENS
    }

    public ParsedModulePackageDirective {
        Objects.requireNonNull(kind, "kind");
        packageName = name(packageName, "packageName");
        if (flags < 0 || flags > 0xffff) throw new IllegalArgumentException("flags must be an unsigned u2");
        Objects.requireNonNull(targetModules, "targetModules");
        targetModules = targetModules.stream()
                .map(value -> name(value, "targetModule"))
                .sorted()
                .toList();
    }

    public boolean qualified() {
        return !targetModules.isEmpty();
    }

    @Override
    public int compareTo(ParsedModulePackageDirective other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = packageName.compareTo(other.packageName);
        if (result != 0) return result;
        result = Integer.compareUnsigned(flags, other.flags);
        return result != 0 ? result : targetModules.toString().compareTo(other.targetModules.toString());
    }

    private static String name(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
