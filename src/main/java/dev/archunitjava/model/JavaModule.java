package dev.archunitjava.model;

import java.lang.classfile.ClassFile;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable module identity and its explicit, unresolved JPMS directives. */
public record JavaModule(
        JavaModuleIdentity identity,
        int flags,
        Optional<String> version,
        List<JavaModuleRequire> requires,
        List<JavaModulePackageDirective> exports,
        List<JavaModulePackageDirective> opens,
        List<JvmReferenceType> uses,
        List<JavaModuleProvide> provides,
        DeclarationLocation location)
        implements Comparable<JavaModule> {
    public JavaModule {
        Objects.requireNonNull(identity, "identity");
        if (flags < 0 || flags > 0xffff) throw new IllegalArgumentException("flags must be an unsigned u2");
        Objects.requireNonNull(version, "version");
        version = version.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("version must not be blank");
            return value;
        });
        requires = sorted(requires, "require");
        exports = sorted(exports, "export");
        if (exports.stream().anyMatch(value -> value.kind()
                != JavaModulePackageDirectiveKind.EXPORTS)) {
            throw new IllegalArgumentException("exports contains a non-export directive");
        }
        opens = sorted(opens, "open");
        if (opens.stream().anyMatch(value -> value.kind()
                != JavaModulePackageDirectiveKind.OPENS)) {
            throw new IllegalArgumentException("opens contains a non-open directive");
        }
        Objects.requireNonNull(uses, "uses");
        uses = uses.stream()
                .map(value -> Objects.requireNonNull(value, "use"))
                .sorted(Comparator.comparing(JvmReferenceType::binaryName))
                .toList();
        provides = sorted(provides, "provide");
        Objects.requireNonNull(location, "location");
        if (identity.kind() != JavaModuleKind.EXPLICIT
                && (flags != 0
                        || version.isPresent()
                        || !requires.isEmpty()
                        || !exports.isEmpty()
                        || !opens.isEmpty()
                        || !uses.isEmpty()
                        || !provides.isEmpty())) {
            throw new IllegalArgumentException("Only explicit modules carry Module-attribute directives");
        }
    }

    public boolean open() {
        return (flags & ClassFile.ACC_OPEN) != 0;
    }

    @Override
    public int compareTo(JavaModule other) {
        int result = identity.compareTo(other.identity);
        return result != 0 ? result : location.resource().compareTo(other.location.resource());
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, role))
                .sorted()
                .toList();
    }
}
