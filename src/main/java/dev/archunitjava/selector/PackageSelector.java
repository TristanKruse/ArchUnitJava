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
    private final SelectorConstant constant;

    private PackageSelector(SelectorDescription description, Matcher matcher) {
        this(description, matcher, SelectorConstant.CONDITIONAL);
    }

    private PackageSelector(
            SelectorDescription description, Matcher matcher, SelectorConstant constant) {
        this.description = Objects.requireNonNull(description, "description");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.constant = Objects.requireNonNull(constant, "constant");
    }

    public static PackageSelector all() {
        return new PackageSelector(
                new SelectorDescription("all packages"), (value, context) -> true,
                SelectorConstant.UNIVERSAL);
    }

    public static PackageSelector none() {
        return new PackageSelector(
                new SelectorDescription("no packages"), (value, context) -> false,
                SelectorConstant.EMPTY);
    }

    public static PackageSelector allOf(PackageSelector... selectors) {
        return allOf(List.of(Objects.requireNonNull(selectors, "selectors").clone()));
    }

    public static PackageSelector allOf(Collection<PackageSelector> selectors) {
        List<PackageSelector> values = stable(selectors, "AND");
        SelectorConstant constant = values.stream().anyMatch(value -> value.constant == SelectorConstant.EMPTY)
                ? SelectorConstant.EMPTY
                : values.stream().allMatch(value -> value.constant == SelectorConstant.UNIVERSAL)
                        ? SelectorConstant.UNIVERSAL : SelectorConstant.CONDITIONAL;
        Matcher matcher = constant == SelectorConstant.CONDITIONAL
                ? (value, context) -> values.stream().allMatch(selector ->
                        selector.matcher.matches(value, context))
                : (value, context) -> constant == SelectorConstant.UNIVERSAL;
        return new PackageSelector(
                SelectorDescriptions.group("AND", descriptions(values)),
                matcher,
                constant);
    }

    public static PackageSelector anyOf(PackageSelector... selectors) {
        return anyOf(List.of(Objects.requireNonNull(selectors, "selectors").clone()));
    }

    public static PackageSelector anyOf(Collection<PackageSelector> selectors) {
        List<PackageSelector> values = stable(selectors, "OR");
        SelectorConstant constant = values.stream().anyMatch(value -> value.constant == SelectorConstant.UNIVERSAL)
                ? SelectorConstant.UNIVERSAL
                : values.stream().allMatch(value -> value.constant == SelectorConstant.EMPTY)
                        ? SelectorConstant.EMPTY : SelectorConstant.CONDITIONAL;
        Matcher matcher = constant == SelectorConstant.CONDITIONAL
                ? (value, context) -> values.stream().anyMatch(selector ->
                        selector.matcher.matches(value, context))
                : (value, context) -> constant == SelectorConstant.UNIVERSAL;
        return new PackageSelector(
                SelectorDescriptions.group("OR", descriptions(values)),
                matcher,
                constant);
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

    public SelectorConstant constant() {
        return constant;
    }

    public PackageSelector and(PackageSelector other) {
        return allOf(this, Objects.requireNonNull(other, "other"));
    }

    public PackageSelector or(PackageSelector other) {
        return anyOf(this, Objects.requireNonNull(other, "other"));
    }

    public PackageSelector not() {
        SelectorConstant negated = switch (constant) {
            case UNIVERSAL -> SelectorConstant.EMPTY;
            case EMPTY -> SelectorConstant.UNIVERSAL;
            case CONDITIONAL -> SelectorConstant.CONDITIONAL;
        };
        return new PackageSelector(
                SelectorDescriptions.not(description),
                (value, context) -> !matcher.matches(value, context),
                negated);
    }

    public PackageSelector excluding(PackageSelector exclusion) {
        PackageSelector value = Objects.requireNonNull(exclusion, "exclusion");
        SelectorConstant result = constant == SelectorConstant.EMPTY
                        || value.constant == SelectorConstant.UNIVERSAL
                ? SelectorConstant.EMPTY
                : value.constant == SelectorConstant.EMPTY ? constant : SelectorConstant.CONDITIONAL;
        Matcher combined = result == SelectorConstant.CONDITIONAL
                ? (candidate, context) -> matcher.matches(candidate, context)
                        && !value.matcher.matches(candidate, context)
                : (candidate, context) -> result == SelectorConstant.UNIVERSAL;
        return new PackageSelector(
                SelectorDescriptions.excluding(description, value.description),
                combined,
                result);
    }

    boolean matches(JavaPackage value, TypeSelectionContext context) {
        return matcher.matches(Objects.requireNonNull(value, "package"),
                Objects.requireNonNull(context, "context"));
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

    private static List<PackageSelector> stable(
            Collection<PackageSelector> selectors, String operator) {
        Objects.requireNonNull(selectors, "selectors");
        List<PackageSelector> values = selectors.stream()
                .map(value -> Objects.requireNonNull(value, "selector"))
                .sorted(java.util.Comparator.comparing(value -> value.description.text()))
                .toList();
        if (values.isEmpty()) {
            throw new IllegalArgumentException(operator + " group must contain at least one selector");
        }
        return values;
    }

    private static List<SelectorDescription> descriptions(List<PackageSelector> selectors) {
        return selectors.stream().map(PackageSelector::description).toList();
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(JavaPackage value, TypeSelectionContext context);
    }
}
