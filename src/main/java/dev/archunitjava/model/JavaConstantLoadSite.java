package dev.archunitjava.model;

import java.util.Objects;

/** Exact member and bytecode location that loaded a class literal. */
public record JavaConstantLoadSite(JavaMemberSignature owner, BytecodeLocation location)
        implements Comparable<JavaConstantLoadSite> {
    public JavaConstantLoadSite {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(location, "location");
    }

    @Override
    public int compareTo(JavaConstantLoadSite other) {
        int result = owner.compareTo(other.owner);
        if (result != 0) return result;
        result = location.resource().compareTo(other.location.resource());
        return result != 0 ? result : Integer.compare(
                location.bytecodeOffset(), other.location.bytecodeOffset());
    }
}
