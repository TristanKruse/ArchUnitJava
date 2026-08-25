package dev.archunitjava.model;

import java.util.Objects;

/** Unresolved constant-pool member target. The owner may be an array type. */
public record JavaCodeAccessTarget(
        JvmType ownerType, String name, String descriptor, boolean method)
        implements Comparable<JavaCodeAccessTarget> {
    public JavaCodeAccessTarget {
        Objects.requireNonNull(ownerType, "ownerType");
        if (!(ownerType instanceof JvmReferenceType || ownerType instanceof JvmArrayType)) {
            throw new IllegalArgumentException("Access target owner must be a reference or array type");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
        if (method) JvmDescriptors.parseMethod(descriptor);
        else JvmDescriptors.parseField(descriptor);
    }

    public String stableKey() {
        return ownerType.descriptor() + "#" + name + descriptor;
    }

    @Override
    public int compareTo(JavaCodeAccessTarget other) {
        return stableKey().compareTo(other.stableKey());
    }
}
