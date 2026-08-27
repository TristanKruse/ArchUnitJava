package dev.archunitjava.rules;

import dev.archunitjava.graph.StableId;
import dev.archunitjava.model.JvmArrayType;
import dev.archunitjava.model.JvmDescriptors;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.JvmType;

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
        JvmType owner = JvmDescriptors.parseField(ownerDescriptor);
        if (!(owner instanceof JvmReferenceType || owner instanceof JvmArrayType)) {
            throw new IllegalArgumentException("ownerDescriptor must identify a reference or array");
        }
        boolean method = descriptor.startsWith("(");
        if (method) JvmDescriptors.parseMethod(descriptor);
        else JvmDescriptors.parseField(descriptor);
        if (name.equals("<init>") && (!method || !descriptor.endsWith(")V"))) {
            throw new IllegalArgumentException("constructor targets must use a void method descriptor");
        }
    }

    @Override
    public String stableKey() {
        return "symbolic-member:" + ownerDescriptor + "#" + name + descriptor;
    }
}
