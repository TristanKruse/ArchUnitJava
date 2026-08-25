package dev.archunitjava.model;

import dev.archunitjava.importer.ClassFileInput;
import java.util.Objects;

/** Safe identity of one classpath input contributing to a package. */
public record JavaPackageOrigin(
        ClassFileInput.Kind kind, String container, int precedence)
        implements Comparable<JavaPackageOrigin> {
    public JavaPackageOrigin {
        Objects.requireNonNull(kind, "kind");
        if (container == null || container.isBlank()) {
            throw new IllegalArgumentException("container must not be blank");
        }
        if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
    }

    static JavaPackageOrigin from(ClassResourceLocation location) {
        return new JavaPackageOrigin(
                location.kind(), location.container(), location.precedence());
    }

    @Override
    public int compareTo(JavaPackageOrigin other) {
        int result = Integer.compare(precedence, other.precedence);
        if (result != 0) return result;
        result = kind.compareTo(other.kind);
        return result != 0 ? result : container.compareTo(other.container);
    }
}
