package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Immutable record component, distinct from its generated field and accessor. */
public record JavaRecordComponent(
        JavaTypeName owner,
        String name,
        String descriptor,
        GenericFieldView typeView,
        List<JavaAnnotationOccurrence> annotations,
        DeclarationLocation location)
        implements Comparable<JavaRecordComponent> {
    public JavaRecordComponent {
        Objects.requireNonNull(owner, "owner");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
        Objects.requireNonNull(typeView, "typeView");
        if (!typeView.erasedType().descriptor().equals(descriptor)) {
            throw new IllegalArgumentException("Record component descriptor and erased type differ");
        }
        Objects.requireNonNull(annotations, "annotations");
        annotations = annotations.stream()
                .map(value -> Objects.requireNonNull(value, "annotation"))
                .sorted()
                .toList();
        Objects.requireNonNull(location, "location");
    }

    public JvmType type() {
        return typeView.erasedType();
    }

    @Override
    public int compareTo(JavaRecordComponent other) {
        int result = owner.compareTo(other.owner);
        if (result != 0) return result;
        result = name.compareTo(other.name);
        return result != 0 ? result : descriptor.compareTo(other.descriptor);
    }
}
