package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Parsed generic signature of a method or constructor. */
public record GenericMethodSignature(
        List<GenericTypeParameter> typeParameters,
        List<GenericType> parameterTypes,
        GenericType returnType,
        List<GenericType> throwsTypes) {
    public GenericMethodSignature {
        Objects.requireNonNull(typeParameters, "typeParameters");
        typeParameters = List.copyOf(typeParameters);
        typeParameters.forEach(value -> Objects.requireNonNull(value, "typeParameter"));
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        parameterTypes = List.copyOf(parameterTypes);
        parameterTypes.forEach(value -> {
            Objects.requireNonNull(value, "parameterType");
            if (value instanceof GenericType.VoidType) {
                throw new IllegalArgumentException("Method parameters cannot be void");
            }
        });
        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(throwsTypes, "throwsTypes");
        throwsTypes = List.copyOf(throwsTypes);
        throwsTypes.forEach(value -> {
            Objects.requireNonNull(value, "throwsType");
            if (!(value instanceof GenericType.ClassType || value instanceof GenericType.TypeVariable)) {
                throw new IllegalArgumentException("Throws types must be classes or type variables");
            }
        });
    }

    public List<JvmReferenceType> referencedTypes() {
        TreeSet<String> result = new TreeSet<>();
        typeParameters.forEach(parameter -> {
            parameter.classBound().ifPresent(value -> add(result, value));
            parameter.interfaceBounds().forEach(value -> add(result, value));
        });
        parameterTypes.forEach(value -> add(result, value));
        add(result, returnType);
        throwsTypes.forEach(value -> add(result, value));
        return result.stream().map(JvmReferenceType::new).toList();
    }

    private static void add(TreeSet<String> result, GenericType type) {
        type.referencedTypes().forEach(value -> result.add(value.binaryName()));
    }
}
