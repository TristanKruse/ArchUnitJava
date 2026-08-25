package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Backend-neutral explicit JPMS Module attribute. */
public record ParsedModuleDescriptor(
        String moduleName,
        int flags,
        Optional<String> version,
        List<ParsedModuleRequire> requires,
        List<ParsedModulePackageDirective> exports,
        List<ParsedModulePackageDirective> opens,
        List<String> usesBinaryNames,
        List<ParsedModuleProvide> provides) {
    public ParsedModuleDescriptor {
        if (moduleName == null || moduleName.isBlank()) {
            throw new IllegalArgumentException("moduleName must not be blank");
        }
        if (flags < 0 || flags > 0xffff) throw new IllegalArgumentException("flags must be an unsigned u2");
        Objects.requireNonNull(version, "version");
        version = version.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("version must not be blank");
            return value;
        });
        requires = sorted(requires, "require");
        exports = sorted(exports, "export");
        if (exports.stream().anyMatch(value -> value.kind()
                != ParsedModulePackageDirective.Kind.EXPORTS)) {
            throw new IllegalArgumentException("exports contains a non-export directive");
        }
        opens = sorted(opens, "open");
        if (opens.stream().anyMatch(value -> value.kind()
                != ParsedModulePackageDirective.Kind.OPENS)) {
            throw new IllegalArgumentException("opens contains a non-open directive");
        }
        Objects.requireNonNull(usesBinaryNames, "usesBinaryNames");
        usesBinaryNames = usesBinaryNames.stream()
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException("usesBinaryName must not be blank");
                    }
                    return value;
                })
                .sorted()
                .toList();
        provides = sorted(provides, "provide");
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, role))
                .sorted()
                .toList();
    }
}
