package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** One library-owned InnerClasses entry; it is evidence, not inferred source nesting. */
public record JavaInnerClassEvidence(
        JavaTypeName innerType,
        Optional<JavaTypeName> outerType,
        Optional<String> simpleName,
        int accessFlags)
        implements Comparable<JavaInnerClassEvidence> {
    public JavaInnerClassEvidence {
        Objects.requireNonNull(innerType, "innerType");
        Objects.requireNonNull(outerType, "outerType");
        Objects.requireNonNull(simpleName, "simpleName");
        simpleName = simpleName.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("simpleName must not be blank");
            return value;
        });
    }

    @Override
    public int compareTo(JavaInnerClassEvidence other) {
        int result = innerType.compareTo(other.innerType);
        if (result != 0) return result;
        result = outerType.map(JavaTypeName::binaryName).orElse("")
                .compareTo(other.outerType.map(JavaTypeName::binaryName).orElse(""));
        if (result != 0) return result;
        result = simpleName.orElse("").compareTo(other.simpleName.orElse(""));
        return result != 0 ? result : Integer.compareUnsigned(accessFlags, other.accessFlags);
    }
}
