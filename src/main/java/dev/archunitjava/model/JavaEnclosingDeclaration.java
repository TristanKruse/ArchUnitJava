package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** Exact EnclosingMethod evidence; the method is absent when the attribute names only a class. */
public record JavaEnclosingDeclaration(
        JavaTypeName owner, Optional<String> methodName, Optional<String> methodDescriptor)
        implements Comparable<JavaEnclosingDeclaration> {
    public JavaEnclosingDeclaration {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(methodDescriptor, "methodDescriptor");
        if (methodName.isPresent() != methodDescriptor.isPresent()) {
            throw new IllegalArgumentException("Enclosing method name and descriptor must occur together");
        }
        methodName = methodName.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("methodName must not be blank");
            return value;
        });
        methodDescriptor.ifPresent(JvmDescriptors::parseMethod);
    }

    @Override
    public int compareTo(JavaEnclosingDeclaration other) {
        int result = owner.compareTo(other.owner);
        if (result != 0) return result;
        result = methodName.orElse("").compareTo(other.methodName.orElse(""));
        return result != 0 ? result : methodDescriptor.orElse("")
                .compareTo(other.methodDescriptor.orElse(""));
    }
}
