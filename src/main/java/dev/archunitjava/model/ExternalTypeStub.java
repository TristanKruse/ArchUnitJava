package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Caller-supplied hierarchy facts for a type that was not imported. */
public record ExternalTypeStub(
        JavaTypeName name,
        Optional<JavaTypeKind> kind,
        Optional<JvmReferenceType> superclass,
        List<JvmReferenceType> directInterfaces,
        boolean hierarchyComplete) {
    public ExternalTypeStub {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(superclass, "superclass");
        Objects.requireNonNull(directInterfaces, "directInterfaces");
        TreeSet<String> sorted = new TreeSet<>();
        directInterfaces.forEach(type -> sorted.add(
                Objects.requireNonNull(type, "directInterface").binaryName()));
        directInterfaces = sorted.stream().map(JvmReferenceType::new).toList();
    }

    public static ExternalTypeStub incomplete(String binaryName) {
        return new ExternalTypeStub(
                new JavaTypeName(binaryName), Optional.empty(), Optional.empty(), List.of(), false);
    }

    public static ExternalTypeStub complete(
            String binaryName,
            JavaTypeKind kind,
            JvmReferenceType superclass,
            List<JvmReferenceType> interfaces) {
        return new ExternalTypeStub(
                new JavaTypeName(binaryName),
                Optional.of(Objects.requireNonNull(kind, "kind")),
                Optional.ofNullable(superclass),
                interfaces,
                true);
    }
}
