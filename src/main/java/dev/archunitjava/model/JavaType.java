package dev.archunitjava.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.TreeSet;
import java.util.Optional;

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
    private final Optional<JvmReferenceType> superclass;
    private final List<JvmReferenceType> directInterfaces;
    private final List<JavaAnnotationOccurrence> annotations;
    private final GenericClassView genericView;
    private final List<JavaRecordComponent> recordComponents;
    private final boolean sealedDeclaration;
    private final List<JvmReferenceType> permittedSubclasses;
    private final JavaNesting nesting;

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
            Optional<JvmReferenceType> superclass,
            List<JvmReferenceType> directInterfaces,
            List<JavaAnnotationOccurrence> annotations,
            GenericClassView genericView,
            List<JavaRecordComponent> recordComponents,
            boolean sealedDeclaration,
            List<JvmReferenceType> permittedSubclasses,
            JavaNesting nesting,
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
        this.superclass = Objects.requireNonNull(superclass, "superclass");
        Objects.requireNonNull(directInterfaces, "directInterfaces");
        TreeSet<String> seenInterfaces = new TreeSet<>();
        for (JvmReferenceType directInterface : directInterfaces) {
            seenInterfaces.add(Objects.requireNonNull(directInterface, "directInterface").binaryName());
        }
        this.directInterfaces = seenInterfaces.stream().map(JvmReferenceType::new).toList();
        Objects.requireNonNull(annotations, "annotations");
        this.annotations = annotations.stream()
                .map(value -> Objects.requireNonNull(value, "annotation"))
                .sorted()
                .toList();
        this.genericView = Objects.requireNonNull(genericView, "genericView");
        if (!this.superclass.equals(genericView.erasedSuperclass())
                || !this.directInterfaces.equals(genericView.erasedInterfaces())) {
            throw new IllegalArgumentException("Generic view must retain the erased hierarchy");
        }
        Objects.requireNonNull(recordComponents, "recordComponents");
        TreeSet<JavaRecordComponent> sortedComponents = new TreeSet<>();
        for (JavaRecordComponent component : recordComponents) {
            JavaRecordComponent value = Objects.requireNonNull(component, "recordComponent");
            if (!value.owner().equals(name)) {
                throw new IllegalArgumentException("Record component owner must match its type");
            }
            if (!sortedComponents.add(value)) {
                throw new IllegalArgumentException("Duplicate record component: " + value.name());
            }
        }
        if (kind != JavaTypeKind.RECORD && !sortedComponents.isEmpty()) {
            throw new IllegalArgumentException("Only records can declare record components");
        }
        this.recordComponents = List.copyOf(sortedComponents);
        this.sealedDeclaration = sealedDeclaration;
        Objects.requireNonNull(permittedSubclasses, "permittedSubclasses");
        this.permittedSubclasses = permittedSubclasses.stream()
                .map(value -> Objects.requireNonNull(value, "permittedSubclass"))
                .sorted(java.util.Comparator.comparing(JvmReferenceType::binaryName))
                .toList();
        if (!sealedDeclaration && !this.permittedSubclasses.isEmpty()) {
            throw new IllegalArgumentException("Permitted subclasses require a sealed declaration");
        }
        this.nesting = Objects.requireNonNull(nesting, "nesting");
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

    public JavaPackageName packageName() {
        return new JavaPackageName(name.packageName());
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

    public Optional<JvmReferenceType> superclass() {
        return superclass;
    }

    public List<JvmReferenceType> directInterfaces() {
        return directInterfaces;
    }

    public List<JavaAnnotationOccurrence> annotations() {
        return annotations;
    }

    public GenericClassView genericView() {
        return genericView;
    }

    public List<JavaRecordComponent> recordComponents() {
        return recordComponents;
    }

    public boolean isSealed() {
        return sealedDeclaration;
    }

    /** Declared permitted subclasses; this is not a list of observed direct subclasses. */
    public List<JvmReferenceType> permittedSubclasses() {
        return permittedSubclasses;
    }

    public JavaNesting nesting() {
        return nesting;
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
