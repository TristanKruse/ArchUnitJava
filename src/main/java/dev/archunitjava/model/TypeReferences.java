package dev.archunitjava.model;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

final class TypeReferences {
    private TypeReferences() {}

    static List<JvmReferenceType> fromJvmTypes(Collection<? extends JvmType> types) {
        TreeSet<String> result = new TreeSet<>();
        types.forEach(type -> add(type, result));
        return names(result);
    }

    static List<JvmReferenceType> merge(
            Collection<JvmReferenceType> first, Collection<JvmReferenceType> second) {
        TreeSet<String> result = new TreeSet<>();
        first.forEach(value -> result.add(value.binaryName()));
        second.forEach(value -> result.add(value.binaryName()));
        return names(result);
    }

    private static void add(JvmType type, TreeSet<String> result) {
        if (type instanceof JvmReferenceType reference) {
            result.add(reference.binaryName());
        } else if (type instanceof JvmArrayType array) {
            add(array.elementType(), result);
        }
    }

    private static List<JvmReferenceType> names(TreeSet<String> names) {
        return names.stream().map(JvmReferenceType::new).toList();
    }
}
