package dev.archunitjava.rules;

import dev.archunitjava.graph.StableId;
import dev.archunitjava.model.JavaModuleIdentity;
import dev.archunitjava.model.JavaModuleKind;
import java.util.Objects;

/** Stable result identity that cannot collapse explicit, automatic, and unnamed modules. */
public record ModuleIdentityId(JavaModuleKind kind, String identity) implements StableId {
    public ModuleIdentityId {
        Objects.requireNonNull(kind, "kind");
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("identity must not be blank");
        }
    }

    public static ModuleIdentityId of(JavaModuleIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return new ModuleIdentityId(
                identity.kind(), identity.name().orElseGet(identity.unnamedOrigin()::orElseThrow));
    }

    @Override
    public String stableKey() {
        return "module-descriptor:" + kind + ":" + identity;
    }
}
