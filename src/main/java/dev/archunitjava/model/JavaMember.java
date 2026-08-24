package dev.archunitjava.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable declared field, method, constructor, or static initializer. */
public final class JavaMember implements Comparable<JavaMember> {
    private final JavaMemberSignature signature;
    private final JavaMemberKind kind;
    private final Set<JavaMemberModifier> modifiers;
    private final int accessFlags;
    private final int unrecognizedAccessFlags;
    private final boolean hasCode;
    private final DeclarationLocation location;
    private final LineNumberTable lineNumbers;

    JavaMember(
            JavaMemberSignature signature,
            JavaMemberKind kind,
            Set<JavaMemberModifier> modifiers,
            int accessFlags,
            int unrecognizedAccessFlags,
            boolean hasCode,
            DeclarationLocation location,
            LineNumberTable lineNumbers) {
        this.signature = Objects.requireNonNull(signature, "signature");
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(modifiers, "modifiers");
        EnumSet<JavaMemberModifier> copy = modifiers.isEmpty()
                ? EnumSet.noneOf(JavaMemberModifier.class)
                : EnumSet.copyOf(modifiers);
        this.modifiers = Collections.unmodifiableSet(copy);
        this.accessFlags = accessFlags;
        this.unrecognizedAccessFlags = unrecognizedAccessFlags;
        if (kind == JavaMemberKind.FIELD && hasCode) {
            throw new IllegalArgumentException("fields cannot contain bytecode");
        }
        this.hasCode = hasCode;
        this.location = Objects.requireNonNull(location, "location");
        this.lineNumbers = Objects.requireNonNull(lineNumbers, "lineNumbers");
        if (!hasCode && !lineNumbers.entries().isEmpty()) {
            throw new IllegalArgumentException("Members without bytecode cannot have line numbers");
        }
    }

    public JavaMemberSignature signature() {
        return signature;
    }

    public JavaTypeName owner() {
        return signature.owner();
    }

    public String name() {
        return signature.name();
    }

    public String descriptor() {
        return signature.descriptor();
    }

    public JavaMemberKind kind() {
        return kind;
    }

    public Set<JavaMemberModifier> modifiers() {
        return modifiers;
    }

    public int accessFlags() {
        return accessFlags;
    }

    public int unrecognizedAccessFlags() {
        return unrecognizedAccessFlags;
    }

    public boolean hasCode() {
        return hasCode;
    }

    public boolean isCodeUnit() {
        return kind != JavaMemberKind.FIELD;
    }

    public DeclarationLocation location() {
        return location;
    }

    public LineNumberTable lineNumbers() {
        return lineNumbers;
    }

    public BytecodeLocation bytecodeLocation(int bytecodeOffset) {
        if (!hasCode) throw new IllegalStateException("Member has no bytecode");
        return new BytecodeLocation(
                location.resource(),
                location.sourceFile(),
                bytecodeOffset,
                lineNumbers.lineAt(bytecodeOffset));
    }

    public JvmType fieldType() {
        if (kind != JavaMemberKind.FIELD) {
            throw new IllegalStateException("Only fields have a field type");
        }
        return JvmDescriptors.parseField(descriptor());
    }

    public JvmMethodType methodType() {
        if (!isCodeUnit()) {
            throw new IllegalStateException("Fields do not have a method type");
        }
        return JvmDescriptors.parseMethod(descriptor());
    }

    @Override
    public int compareTo(JavaMember other) {
        return signature.compareTo(other.signature);
    }
}
