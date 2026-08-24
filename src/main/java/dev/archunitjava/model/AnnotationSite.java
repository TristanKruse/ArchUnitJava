package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Exact owner and sub-location of an annotation occurrence. */
public record AnnotationSite(
        AnnotationSiteKind kind,
        String ownerKey,
        OptionalInt parameterIndex,
        Optional<JavaTypeUseTarget> typeUseTarget) {
    public AnnotationSite {
        Objects.requireNonNull(kind, "kind");
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("ownerKey must not be blank");
        }
        Objects.requireNonNull(parameterIndex, "parameterIndex");
        Objects.requireNonNull(typeUseTarget, "typeUseTarget");
        if (kind == AnnotationSiteKind.PARAMETER != parameterIndex.isPresent()) {
            throw new IllegalArgumentException("Only parameter sites have a parameter index");
        }
        if (kind == AnnotationSiteKind.TYPE_USE != typeUseTarget.isPresent()) {
            throw new IllegalArgumentException("Only type-use sites have a type-use target");
        }
    }

    public String stableKey() {
        return kind + ":" + ownerKey + ":"
                + (parameterIndex.isPresent() ? parameterIndex.getAsInt() : "-") + ":"
                + typeUseTarget.map(JavaTypeUseTarget::stableKey).orElse("");
    }
}
