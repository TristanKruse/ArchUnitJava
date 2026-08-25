package dev.archunitjava.model;

import dev.archunitjava.graph.ModuleId;
import java.util.Objects;
import java.util.Optional;

/** JPMS identity that never conflates explicit, automatic, and unnamed modules. */
public record JavaModuleIdentity(
        JavaModuleKind kind, Optional<String> name, Optional<String> unnamedOrigin)
        implements Comparable<JavaModuleIdentity> {
    public JavaModuleIdentity {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unnamedOrigin, "unnamedOrigin");
        name = name.map(value -> ModuleId.named(value).name());
        unnamedOrigin = unnamedOrigin.map(value -> text(value, "unnamedOrigin"));
        boolean unnamed = kind == JavaModuleKind.UNNAMED;
        if (unnamed == name.isPresent() || unnamed != unnamedOrigin.isPresent()) {
            throw new IllegalArgumentException("Named and unnamed module identity data disagree");
        }
    }

    public static JavaModuleIdentity explicit(String name) {
        return new JavaModuleIdentity(
                JavaModuleKind.EXPLICIT, Optional.of(name), Optional.empty());
    }

    public static JavaModuleIdentity automatic(String name) {
        return new JavaModuleIdentity(
                JavaModuleKind.AUTOMATIC, Optional.of(name), Optional.empty());
    }

    public static JavaModuleIdentity unnamed(String originIdentity) {
        return new JavaModuleIdentity(
                JavaModuleKind.UNNAMED, Optional.empty(), Optional.of(originIdentity));
    }

    public String stableKey() {
        return kind + ":" + name.orElseGet(unnamedOrigin::orElseThrow);
    }

    @Override
    public int compareTo(JavaModuleIdentity other) {
        return stableKey().compareTo(other.stableKey());
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
