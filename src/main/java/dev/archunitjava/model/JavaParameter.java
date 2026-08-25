package dev.archunitjava.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** One descriptor parameter with optional MethodParameters name and exact flags. */
public record JavaParameter(
        JavaMemberSignature owner,
        int index,
        JvmType type,
        Optional<String> name,
        Set<JavaParameterModifier> modifiers,
        int accessFlags,
        int unrecognizedAccessFlags)
        implements Comparable<JavaParameter> {
    public JavaParameter {
        Objects.requireNonNull(owner, "owner");
        if (index < 0) throw new IllegalArgumentException("index must not be negative");
        Objects.requireNonNull(type, "type");
        if (type instanceof JvmVoidType) {
            throw new IllegalArgumentException("parameters cannot have void type");
        }
        Objects.requireNonNull(name, "name");
        name = name.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("parameter name must not be blank");
            return value;
        });
        Objects.requireNonNull(modifiers, "modifiers");
        EnumSet<JavaParameterModifier> copy = modifiers.isEmpty()
                ? EnumSet.noneOf(JavaParameterModifier.class)
                : EnumSet.copyOf(modifiers);
        modifiers = Collections.unmodifiableSet(copy);
    }

    @Override
    public int compareTo(JavaParameter other) {
        int result = owner.compareTo(other.owner);
        return result != 0 ? result : Integer.compare(index, other.index);
    }
}
