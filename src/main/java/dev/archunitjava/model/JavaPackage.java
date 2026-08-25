package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Aggregated Java package with metadata carriers separated from ordinary types. */
public record JavaPackage(
        JavaPackageName name,
        List<JavaType> types,
        List<JavaType> packageInfoTypes,
        List<JavaAnnotationOccurrence> annotations,
        List<JavaPackageOrigin> origins)
        implements Comparable<JavaPackage> {
    public JavaPackage {
        Objects.requireNonNull(name, "name");
        types = sorted(types, "type");
        packageInfoTypes = sorted(packageInfoTypes, "packageInfoType");
        Objects.requireNonNull(annotations, "annotations");
        annotations = annotations.stream()
                .map(value -> Objects.requireNonNull(value, "annotation"))
                .sorted()
                .toList();
        Objects.requireNonNull(origins, "origins");
        origins = origins.stream()
                .map(value -> Objects.requireNonNull(value, "origin"))
                .distinct()
                .sorted()
                .toList();
    }

    public boolean isSplitAcrossOrigins() {
        return origins.size() > 1;
    }

    public Optional<JavaType> packageInfoType() {
        return packageInfoTypes.size() == 1
                ? Optional.of(packageInfoTypes.getFirst())
                : Optional.empty();
    }

    static JavaAnnotationOccurrence packageAnnotation(
            JavaPackageName packageName, JavaAnnotationOccurrence occurrence) {
        return new JavaAnnotationOccurrence(
                occurrence.visibility(),
                new AnnotationSite(
                        AnnotationSiteKind.PACKAGE_DECLARATION,
                        "package:" + packageName.displayName(),
                        OptionalInt.empty(),
                        Optional.empty()),
                occurrence.annotation());
    }

    private static List<JavaType> sorted(List<JavaType> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, role))
                .sorted()
                .toList();
    }

    @Override
    public int compareTo(JavaPackage other) {
        return name.compareTo(other.name);
    }
}
