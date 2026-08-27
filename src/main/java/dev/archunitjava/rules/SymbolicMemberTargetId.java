package dev.archunitjava.rules;

import dev.archunitjava.graph.StableId;

/** A bytecode member reference that deliberately does not claim a resolved runtime declaration. */
public record SymbolicMemberTargetId(
        String ownerDescriptor, String name, String descriptor) implements StableId {
    public SymbolicMemberTargetId {
        if (ownerDescriptor == null || ownerDescriptor.isBlank()) {
            throw new IllegalArgumentException("ownerDescriptor must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
    }

    @Override
    public String stableKey() {
        return "symbolic-member:" + ownerDescriptor + "#" + name + descriptor;
    }
}
