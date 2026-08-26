package dev.archunitjava.selector;

import dev.archunitjava.model.AnnotationVisibility;
import dev.archunitjava.model.JavaTypeName;
import dev.archunitjava.model.MetaAnnotationResolver;
import java.util.Objects;
import java.util.Optional;

/** Immutable annotation target, relationship, visibility, traversal bound, and unknown policy. */
public record AnnotationQuery(
        JavaTypeName annotationType,
        AnnotationMatchMode mode,
        Optional<AnnotationVisibility> visibility,
        int maximumMetaDepth,
        UnknownHierarchyPolicy unknownPolicy) {
    public AnnotationQuery {
        Objects.requireNonNull(annotationType, "annotationType");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(visibility, "visibility");
        if (maximumMetaDepth < 0 || maximumMetaDepth > MetaAnnotationResolver.MAXIMUM_DEPTH) {
            throw new IllegalArgumentException(
                    "maximumMetaDepth must be between 0 and " + MetaAnnotationResolver.MAXIMUM_DEPTH);
        }
        Objects.requireNonNull(unknownPolicy, "unknownPolicy");
    }

    public static AnnotationQuery direct(String annotationBinaryName) {
        return query(annotationBinaryName, AnnotationMatchMode.DIRECT_DECLARATION);
    }

    public static AnnotationQuery metaAnnotated(String annotationBinaryName) {
        return query(annotationBinaryName, AnnotationMatchMode.META_ANNOTATION);
    }

    public static AnnotationQuery inherited(String annotationBinaryName) {
        return query(annotationBinaryName, AnnotationMatchMode.INHERITED_DECLARATION);
    }

    public static AnnotationQuery typeUse(String annotationBinaryName) {
        return query(annotationBinaryName, AnnotationMatchMode.TYPE_USE);
    }

    public AnnotationQuery withVisibility(AnnotationVisibility value) {
        return new AnnotationQuery(
                annotationType, mode, Optional.of(Objects.requireNonNull(value, "visibility")),
                maximumMetaDepth, unknownPolicy);
    }

    public AnnotationQuery withMaximumMetaDepth(int value) {
        return new AnnotationQuery(annotationType, mode, visibility, value, unknownPolicy);
    }

    public AnnotationQuery withUnknownPolicy(UnknownHierarchyPolicy value) {
        return new AnnotationQuery(
                annotationType, mode, visibility, maximumMetaDepth,
                Objects.requireNonNull(value, "unknownPolicy"));
    }

    private static AnnotationQuery query(String binaryName, AnnotationMatchMode mode) {
        return new AnnotationQuery(
                new JavaTypeName(binaryName), mode, Optional.empty(),
                MetaAnnotationResolver.MAXIMUM_DEPTH, UnknownHierarchyPolicy.EXCLUDE);
    }
}
