package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Parsed generic signature of a class or interface. */
public record GenericClassSignature(
        List<GenericTypeParameter> typeParameters,
        GenericType.ClassType superclass,
        List<GenericType.ClassType> interfaces) {
    public GenericClassSignature {
        Objects.requireNonNull(typeParameters, "typeParameters");
        typeParameters = List.copyOf(typeParameters);
        typeParameters.forEach(value -> Objects.requireNonNull(value, "typeParameter"));
        Objects.requireNonNull(superclass, "superclass");
        Objects.requireNonNull(interfaces, "interfaces");
        interfaces = List.copyOf(interfaces);
        interfaces.forEach(value -> Objects.requireNonNull(value, "interface"));
    }

    public List<JvmReferenceType> referencedTypes() {
        TreeSet<String> result = new TreeSet<>();
        add(result, superclass);
        interfaces.forEach(value -> add(result, value));
        typeParameters.forEach(parameter -> {
            parameter.classBound().ifPresent(value -> add(result, value));
            parameter.interfaceBounds().forEach(value -> add(result, value));
        });
        return result.stream().map(JvmReferenceType::new).toList();
    }

    private static void add(TreeSet<String> result, GenericType type) {
        type.referencedTypes().forEach(value -> result.add(value.binaryName()));
    }
}
