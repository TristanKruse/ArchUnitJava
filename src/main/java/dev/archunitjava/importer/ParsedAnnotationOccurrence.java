package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Exact declaration, parameter, record-component, or type-use annotation site. */
public record ParsedAnnotationOccurrence(
        Visibility visibility,
        Container container,
        String ownerName,
        String ownerDescriptor,
        Site site,
        OptionalInt parameterIndex,
        Optional<TypeUseTarget> typeUseTarget,
        ParsedAnnotation annotation)
        implements Comparable<ParsedAnnotationOccurrence> {
    public enum Visibility {
        RUNTIME_VISIBLE,
        RUNTIME_INVISIBLE
    }

    public enum Container {
        TYPE,
        FIELD,
        METHOD,
        RECORD_COMPONENT
    }

    public enum Site {
        DECLARATION,
        PARAMETER,
        TYPE_USE
    }

    public record TypeUseTarget(String targetType, String targetInfo, List<String> path) {
        public TypeUseTarget {
            targetType = requireText(targetType, "targetType");
            Objects.requireNonNull(targetInfo, "targetInfo");
            Objects.requireNonNull(path, "path");
            path = List.copyOf(path);
            path.forEach(value -> requireText(value, "path component"));
        }

        String stableKey() {
            return targetType + ":" + targetInfo + ":" + String.join("/", path);
        }
    }

    public ParsedAnnotationOccurrence {
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(parameterIndex, "parameterIndex");
        Objects.requireNonNull(typeUseTarget, "typeUseTarget");
        Objects.requireNonNull(annotation, "annotation");
        ownerName = ownerName == null ? "" : ownerName;
        ownerDescriptor = ownerDescriptor == null ? "" : ownerDescriptor;
        if (container == Container.TYPE && (!ownerName.isEmpty() || !ownerDescriptor.isEmpty())) {
            throw new IllegalArgumentException("Type annotations do not have a member owner");
        }
        if (container != Container.TYPE && (ownerName.isBlank() || ownerDescriptor.isBlank())) {
            throw new IllegalArgumentException("Non-type annotations require an owner name and descriptor");
        }
        if (site == Site.PARAMETER && parameterIndex.isEmpty()) {
            throw new IllegalArgumentException("Parameter annotations require an index");
        }
        if (site != Site.PARAMETER && parameterIndex.isPresent()) {
            throw new IllegalArgumentException("Only parameter annotations have an index");
        }
        if (site == Site.TYPE_USE && typeUseTarget.isEmpty()) {
            throw new IllegalArgumentException("Type-use annotations require target information");
        }
        if (site != Site.TYPE_USE && typeUseTarget.isPresent()) {
            throw new IllegalArgumentException("Only type-use annotations have target information");
        }
    }

    @Override
    public int compareTo(ParsedAnnotationOccurrence other) {
        return stableKey().compareTo(other.stableKey());
    }

    private String stableKey() {
        return container + ":" + ownerName + ":" + ownerDescriptor + ":" + site + ":"
                + (parameterIndex.isPresent() ? parameterIndex.getAsInt() : "-") + ":"
                + typeUseTarget.map(TypeUseTarget::stableKey).orElse("") + ":"
                + visibility + ":" + annotation.stableKey();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
