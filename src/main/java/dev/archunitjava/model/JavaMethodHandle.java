package dev.archunitjava.model;

import java.util.Objects;

/** Unresolved direct method-handle constant used as bootstrap or implementation evidence. */
public record JavaMethodHandle(
        JavaMethodHandleKind kind,
        int referenceKind,
        JvmType ownerType,
        String name,
        String lookupDescriptor,
        boolean ownerInterface)
        implements Comparable<JavaMethodHandle> {
    public JavaMethodHandle {
        Objects.requireNonNull(kind, "kind");
        if (referenceKind < 1 || referenceKind > 9) {
            throw new IllegalArgumentException("referenceKind must be between 1 and 9");
        }
        Objects.requireNonNull(ownerType, "ownerType");
        if (!(ownerType instanceof JvmReferenceType || ownerType instanceof JvmArrayType)) {
            throw new IllegalArgumentException("Method-handle owner must be a reference or array type");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (lookupDescriptor == null || lookupDescriptor.isBlank()) {
            throw new IllegalArgumentException("lookupDescriptor must not be blank");
        }
    }

    public String stableKey() {
        return kind + ":" + referenceKind + ":" + ownerType.descriptor() + "#"
                + name + lookupDescriptor + ":" + ownerInterface;
    }

    @Override
    public int compareTo(JavaMethodHandle other) {
        return stableKey().compareTo(other.stableKey());
    }
}
