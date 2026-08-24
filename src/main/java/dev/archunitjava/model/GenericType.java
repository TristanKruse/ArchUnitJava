package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** A type expressed by a JVM generic signature, independent of its erased descriptor. */
public sealed interface GenericType
        permits GenericType.ArrayType,
                GenericType.ClassType,
                GenericType.PrimitiveType,
                GenericType.TypeVariable,
                GenericType.VoidType {

    /** All concrete reference types mentioned by this type, in stable order. */
    default List<JvmReferenceType> referencedTypes() {
        TreeSet<String> names = new TreeSet<>();
        collectReferences(this, names);
        return names.stream().map(JvmReferenceType::new).toList();
    }

    private static void collectReferences(GenericType type, TreeSet<String> names) {
        if (type instanceof ClassType classType) {
            names.add(classType.rawType().binaryName());
            classType.owner().ifPresent(owner -> collectReferences(owner, names));
            classType.typeArguments().stream()
                    .flatMap(argument -> argument.type().stream())
                    .forEach(argument -> collectReferences(argument, names));
        } else if (type instanceof ArrayType array) {
            collectReferences(array.elementType(), names);
        }
    }

    record PrimitiveType(JvmPrimitiveType type) implements GenericType {
        public PrimitiveType {
            Objects.requireNonNull(type, "type");
        }
    }

    record VoidType() implements GenericType {}

    record ArrayType(GenericType elementType, int dimensions) implements GenericType {
        public ArrayType {
            Objects.requireNonNull(elementType, "elementType");
            if (elementType instanceof ArrayType || elementType instanceof VoidType) {
                throw new IllegalArgumentException("Array element must be a non-array, non-void type");
            }
            if (dimensions < 1 || dimensions > JvmArrayType.MAXIMUM_DIMENSIONS) {
                throw new IllegalArgumentException("Invalid generic array dimensions: " + dimensions);
            }
        }
    }

    record TypeVariable(String name) implements GenericType {
        public TypeVariable {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Type-variable name must not be blank");
            }
        }
    }

    /**
     * A possibly parameterized class. For nested classes, {@code owner} retains the generic
     * arguments on each enclosing segment while {@code rawType} is the full binary name.
     */
    record ClassType(
            Optional<ClassType> owner,
            JvmReferenceType rawType,
            String simpleName,
            List<GenericTypeArgument> typeArguments)
            implements GenericType {
        public ClassType {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(rawType, "rawType");
            if (simpleName == null || simpleName.isBlank()) {
                throw new IllegalArgumentException("simpleName must not be blank");
            }
            Objects.requireNonNull(typeArguments, "typeArguments");
            typeArguments = typeArguments.stream()
                    .map(value -> Objects.requireNonNull(value, "typeArgument"))
                    .toList();
            owner.ifPresent(value -> {
                String expectedPrefix = value.rawType().binaryName() + "$";
                if (!rawType.binaryName().startsWith(expectedPrefix)) {
                    throw new IllegalArgumentException("Nested raw type does not match its owner");
                }
            });
        }
    }
}
