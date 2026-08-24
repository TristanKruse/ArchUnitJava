package dev.archunitjava.model;

import java.util.Objects;

/** Stable owner/name/descriptor identity for an overloaded JVM member. */
public record JavaMemberSignature(JavaTypeName owner, String name, String descriptor)
        implements Comparable<JavaMemberSignature> {
    public JavaMemberSignature {
        Objects.requireNonNull(owner, "owner");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
    }

    public String stableKey() {
        return owner.binaryName() + "#" + name + descriptor;
    }

    @Override
    public int compareTo(JavaMemberSignature other) {
        int result = owner.compareTo(other.owner);
        if (result != 0) return result;
        result = name.compareTo(other.name);
        return result != 0 ? result : descriptor.compareTo(other.descriptor);
    }
}
