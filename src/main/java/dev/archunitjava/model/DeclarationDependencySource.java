package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** One exact class-file value and owning declaration that produced a dependency. */
public record DeclarationDependencySource(
        DeclarationDependencySourceKind kind,
        DeclarationDependencyOwner owner,
        String classFileValue,
        String detail,
        int arrayDimensions,
        Optional<AnnotationSite> annotationSite,
        DeclarationLocation location)
        implements Comparable<DeclarationDependencySource> {
    public DeclarationDependencySource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        if (classFileValue == null || classFileValue.isBlank()) {
            throw new IllegalArgumentException("classFileValue must not be blank");
        }
        Objects.requireNonNull(detail, "detail");
        if (arrayDimensions < 0 || arrayDimensions > JvmArrayType.MAXIMUM_DIMENSIONS) {
            throw new IllegalArgumentException("Invalid array dimensions: " + arrayDimensions);
        }
        Objects.requireNonNull(annotationSite, "annotationSite");
        Objects.requireNonNull(location, "location");
    }

    @Override
    public int compareTo(DeclarationDependencySource other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = owner.compareTo(other.owner);
        if (result != 0) return result;
        result = classFileValue.compareTo(other.classFileValue);
        if (result != 0) return result;
        result = detail.compareTo(other.detail);
        if (result != 0) return result;
        result = Integer.compare(arrayDimensions, other.arrayDimensions);
        if (result != 0) return result;
        result = annotationSite.map(AnnotationSite::stableKey).orElse("")
                .compareTo(other.annotationSite.map(AnnotationSite::stableKey).orElse(""));
        return result != 0 ? result : location.resource().compareTo(other.location.resource());
    }
}
