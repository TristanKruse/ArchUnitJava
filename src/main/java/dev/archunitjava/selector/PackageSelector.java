package dev.archunitjava.selector;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaPackageIndex;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable, reusable selection of Java package aggregates. */
public final class PackageSelector {
    private final SelectorDescription description;
    private final Matcher matcher;

    private PackageSelector(SelectorDescription description, Matcher matcher) {
        this.description = Objects.requireNonNull(description, "description");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public static PackageSelector all() {
        return new PackageSelector(
                new SelectorDescription("all packages"), (value, context) -> true);
    }

    public static PackageSelector name(JavaPattern pattern) {
        JavaPattern value = Objects.requireNonNull(pattern, "pattern");
        if (value.description().domain() != PatternDomain.QUALIFIED_NAME) {
            throw new IllegalArgumentException("Package name requires QUALIFIED_NAME pattern domain");
        }
        return new PackageSelector(
                new SelectorDescription("package name matches " + value.description()),
                (candidate, context) -> value.matches(candidate.name().value()));
    }

    public static PackageSelector named() {
        return new PackageSelector(
                new SelectorDescription("named packages"),
                (value, context) -> !value.name().isUnnamed());
    }

    public static PackageSelector unnamed() {
        return new PackageSelector(
                new SelectorDescription("unnamed package"),
                (value, context) -> value.name().isUnnamed());
    }

    public static PackageSelector originKind(ClassFileInput.Kind kind) {
        ClassFileInput.Kind value = Objects.requireNonNull(kind, "kind");
        return new PackageSelector(
                new SelectorDescription("packages with origin kind " + value.name()),
                (candidate, context) -> candidate.origins().stream()
                        .anyMatch(origin -> origin.kind() == value));
    }

    public static PackageSelector originContainer(String container) {
        if (container == null || container.isBlank()) {
            throw new IllegalArgumentException("container must not be blank");
        }
        return new PackageSelector(
                new SelectorDescription("packages from container '" + container + "'"),
                (candidate, context) -> candidate.origins().stream()
                        .anyMatch(origin -> origin.container().equals(container)));
    }

    public static PackageSelector containing(TypeSelector selector) {
        TypeSelector value = Objects.requireNonNull(selector, "selector");
        return new PackageSelector(
                new SelectorDescription("packages containing " + value.description()),
                (candidate, context) -> candidate.types().stream()
                        .anyMatch(type -> value.matches(type, context)));
    }

    public SelectorDescription description() {
        return description;
    }

    public PackageSelection selectFrom(JavaPackageIndex index) {
        Objects.requireNonNull(index, "index");
        List<JavaPackage> packages = index.all();
        List<JavaType> universe = packages.stream()
                .map(JavaPackage::types)
                .flatMap(Collection::stream)
                .toList();
        return select(packages, universe, List.of(), List.of());
    }

    public PackageSelection selectFrom(TypeModelResult model) {
        Objects.requireNonNull(model, "model");
        return select(
                model.packages().all(),
                model.types(),
                model.classFileDiagnostics(),
                model.diagnostics());
    }

    private PackageSelection select(
            Collection<JavaPackage> packages,
            Collection<JavaType> universe,
            List<dev.archunitjava.importer.ClassFileDiagnostic> classFileDiagnostics,
            List<dev.archunitjava.model.TypeModelDiagnostic> modelDiagnostics) {
        List<JavaPackage> candidates = packages.stream()
                .map(value -> Objects.requireNonNull(value, "package"))
                .distinct()
                .sorted()
                .toList();
        List<JavaType> types = universe.stream().distinct().sorted().toList();
        TypeSelectionContext context = new TypeSelectionContext(types);
        List<JavaPackage> selected = candidates.stream()
                .filter(value -> matcher.matches(value, context))
                .toList();
        return new PackageSelection(
                description,
                candidates.size(),
                selected,
                classFileDiagnostics,
                modelDiagnostics,
                List.copyOf(context.diagnostics()));
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(JavaPackage value, TypeSelectionContext context);
    }
}
