package dev.archunitjava.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.TreeSet;

/** Immutable top-level Java type description backed only by library-owned values. */
public final class JavaType implements Comparable<JavaType> {
    private final JavaTypeName name;
    private final TypeOwner owner;
    private final JavaTypeKind kind;
    private final Set<JavaModifier> modifiers;
    private final int accessFlags;
    private final int unrecognizedAccessFlags;
    private final ClassFileVersion classFileVersion;
    private final String resourceName;
    private final int precedence;
    private final List<JavaMember> declaredMembers;
    private final DeclarationLocation location;

    JavaType(
            JavaTypeName name,
            JavaTypeKind kind,
            Set<JavaModifier> modifiers,
            int accessFlags,
            int unrecognizedAccessFlags,
            ClassFileVersion classFileVersion,
            String resourceName,
            int precedence,
            DeclarationLocation location,
            List<JavaMember> declaredMembers) {
        this.name = Objects.requireNonNull(name, "name");
        this.owner = new TypeOwner(name.packageName());
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(modifiers, "modifiers");
        EnumSet<JavaModifier> modifierCopy = modifiers.isEmpty()
                ? EnumSet.noneOf(JavaModifier.class)
                : EnumSet.copyOf(modifiers);
        this.modifiers = Collections.unmodifiableSet(modifierCopy);
        this.accessFlags = accessFlags;
        this.unrecognizedAccessFlags = unrecognizedAccessFlags;
        this.classFileVersion = Objects.requireNonNull(classFileVersion, "classFileVersion");
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        this.resourceName = resourceName;
        if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
        this.precedence = precedence;
        this.location = Objects.requireNonNull(location, "location");
        Objects.requireNonNull(declaredMembers, "declaredMembers");
        TreeSet<JavaMember> sortedMembers = new TreeSet<>();
        for (JavaMember member : declaredMembers) {
            JavaMember value = Objects.requireNonNull(member, "declaredMember");
            if (!value.owner().equals(name)) {
                throw new IllegalArgumentException("Declared member owner must match its type");
            }
            if (!sortedMembers.add(value)) {
                throw new IllegalArgumentException(
                        "Duplicate declared member signature: " + value.signature().stableKey());
            }
        }
        this.declaredMembers = List.copyOf(sortedMembers);
    }

    public JavaTypeName name() {
        return name;
    }

    public String binaryName() {
        return name.binaryName();
    }

    public String sourceName() {
        return name.sourceName();
    }

    public TypeOwner owner() {
        return owner;
    }

    public JavaTypeKind kind() {
        return kind;
    }

    public Set<JavaModifier> modifiers() {
        return modifiers;
    }

    /** Exact unsigned-u2 JVM access mask represented as an {@code int}. */
    public int accessFlags() {
        return accessFlags;
    }

    /** Bits not assigned a top-level class meaning by this model version. */
    public int unrecognizedAccessFlags() {
        return unrecognizedAccessFlags;
    }

    public ClassFileVersion classFileVersion() {
        return classFileVersion;
    }

    public String resourceName() {
        return resourceName;
    }

    public int precedence() {
        return precedence;
    }

    public List<JavaMember> declaredMembers() {
        return declaredMembers;
    }

    public DeclarationLocation location() {
        return location;
    }

    @Override
    public int compareTo(JavaType other) {
        int result = Integer.compare(precedence, other.precedence);
        if (result != 0) return result;
        result = name.compareTo(other.name);
        if (result != 0) return result;
        result = resourceName.compareTo(other.resourceName);
        return result != 0 ? result : location.resource().compareTo(other.location.resource());
    }
}
