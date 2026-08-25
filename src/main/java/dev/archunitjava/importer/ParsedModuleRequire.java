package dev.archunitjava.importer;

import java.util.Optional;

/** One requires directive exactly as declared in a Module attribute. */
public record ParsedModuleRequire(String moduleName, int flags, Optional<String> compiledVersion)
        implements Comparable<ParsedModuleRequire> {
    public ParsedModuleRequire {
        moduleName = name(moduleName, "moduleName");
        if (flags < 0 || flags > 0xffff) throw new IllegalArgumentException("flags must be an unsigned u2");
        if (compiledVersion == null) throw new NullPointerException("compiledVersion");
        compiledVersion = compiledVersion.map(value -> name(value, "compiledVersion"));
    }

    @Override
    public int compareTo(ParsedModuleRequire other) {
        int result = moduleName.compareTo(other.moduleName);
        if (result != 0) return result;
        result = Integer.compareUnsigned(flags, other.flags);
        return result != 0 ? result : compiledVersion.orElse("").compareTo(other.compiledVersion.orElse(""));
    }

    private static String name(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
