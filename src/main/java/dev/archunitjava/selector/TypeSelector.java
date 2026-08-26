package dev.archunitjava.selector;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeKind;
import dev.archunitjava.model.JavaModifier;
import dev.archunitjava.model.JavaNestingKind;
import dev.archunitjava.model.JavaTypeName;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable, reusable selection of imported Java types. */
public final class TypeSelector {
    private final SelectorDescription description;
    private final Matcher matcher;
    private final SelectorConstant constant;

    private TypeSelector(SelectorDescription description, Matcher matcher) {
        this(description, matcher, SelectorConstant.CONDITIONAL);
    }

    private TypeSelector(
            SelectorDescription description, Matcher matcher, SelectorConstant constant) {
        this.description = Objects.requireNonNull(description, "description");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.constant = Objects.requireNonNull(constant, "constant");
    }

    public static TypeSelector all() {
        return new TypeSelector(
                new SelectorDescription("all types"),
                (type, context) -> true,
                SelectorConstant.UNIVERSAL);
    }

    public static TypeSelector none() {
        return new TypeSelector(
                new SelectorDescription("no types"),
                (type, context) -> false,
                SelectorConstant.EMPTY);
    }

    public static TypeSelector allOf(TypeSelector... selectors) {
        return allOf(List.of(Objects.requireNonNull(selectors, "selectors").clone()));
    }

    public static TypeSelector allOf(Collection<TypeSelector> selectors) {
        List<TypeSelector> values = stable(selectors, "AND");
        SelectorConstant constant = values.stream().anyMatch(value -> value.constant == SelectorConstant.EMPTY)
                ? SelectorConstant.EMPTY
                : values.stream().allMatch(value -> value.constant == SelectorConstant.UNIVERSAL)
                        ? SelectorConstant.UNIVERSAL : SelectorConstant.CONDITIONAL;
        Matcher matcher = constant == SelectorConstant.CONDITIONAL
                ? (type, context) -> values.stream().allMatch(value -> value.matcher.matches(type, context))
                : (type, context) -> constant == SelectorConstant.UNIVERSAL;
        return new TypeSelector(
                SelectorDescriptions.group("AND", descriptions(values)),
                matcher,
                constant);
    }

    public static TypeSelector anyOf(TypeSelector... selectors) {
        return anyOf(List.of(Objects.requireNonNull(selectors, "selectors").clone()));
    }

    public static TypeSelector anyOf(Collection<TypeSelector> selectors) {
        List<TypeSelector> values = stable(selectors, "OR");
        SelectorConstant constant = values.stream().anyMatch(value -> value.constant == SelectorConstant.UNIVERSAL)
                ? SelectorConstant.UNIVERSAL
                : values.stream().allMatch(value -> value.constant == SelectorConstant.EMPTY)
                        ? SelectorConstant.EMPTY : SelectorConstant.CONDITIONAL;
        Matcher matcher = constant == SelectorConstant.CONDITIONAL
                ? (type, context) -> values.stream().anyMatch(value -> value.matcher.matches(type, context))
                : (type, context) -> constant == SelectorConstant.UNIVERSAL;
        return new TypeSelector(
                SelectorDescriptions.group("OR", descriptions(values)),
                matcher,
                constant);
    }

    public static TypeSelector binaryName(JavaPattern pattern) {
        JavaPattern value = qualified(pattern, "binary name");
        return patternSelector("binary name", value,
                (type, context) -> value.matches(type.binaryName()));
    }

    public static TypeSelector canonicalName(JavaPattern pattern) {
        JavaPattern value = qualified(pattern, "canonical name");
        return patternSelector("canonical name", value,
                (type, context) -> context.canonicalName(type).filter(value::matches).isPresent());
    }

    public static TypeSelector simpleName(JavaPattern pattern) {
        JavaPattern value = qualified(pattern, "simple name");
        return patternSelector("simple name", value,
                (type, context) -> context.simpleName(type).filter(value::matches).isPresent());
    }

    public static TypeSelector packageName(JavaPattern pattern) {
        JavaPattern value = qualified(pattern, "package name");
        return patternSelector("package name", value,
                (type, context) -> value.matches(type.packageName().value()));
    }

    public static TypeSelector resourcePath(JavaPattern pattern) {
        JavaPattern value = resource(pattern, "resource path");
        return patternSelector("resource path", value,
                (type, context) -> value.matches(type.location().resource().entry()));
    }

    public static TypeSelector kind(JavaTypeKind kind) {
        JavaTypeKind value = Objects.requireNonNull(kind, "kind");
        return new TypeSelector(
                new SelectorDescription("types of kind " + value.name()),
                (type, context) -> type.kind() == value);
    }

    public static TypeSelector visibility(TypeVisibility visibility) {
        TypeVisibility value = Objects.requireNonNull(visibility, "visibility");
        return new TypeSelector(
                new SelectorDescription("types with " + value.name() + " visibility"),
                (type, context) -> (value == TypeVisibility.PUBLIC)
                        == type.modifiers().contains(JavaModifier.PUBLIC));
    }

    public static TypeSelector modifier(JavaModifier modifier) {
        JavaModifier value = Objects.requireNonNull(modifier, "modifier");
        return new TypeSelector(
                new SelectorDescription("types with modifier " + value.name()),
                (type, context) -> type.modifiers().contains(value));
    }

    public static TypeSelector records() {
        return kind(JavaTypeKind.RECORD);
    }

    public static TypeSelector sealedTypes() {
        return new TypeSelector(
                new SelectorDescription("sealed types"),
                (type, context) -> type.isSealed());
    }

    public static TypeSelector nesting(JavaNestingKind nesting) {
        JavaNestingKind value = Objects.requireNonNull(nesting, "nesting");
        return new TypeSelector(
                new SelectorDescription("types with nesting kind " + value.name()),
                (type, context) -> type.nesting().kind() == value);
    }

    public static TypeSelector annotatedWith(AnnotationQuery query) {
        AnnotationQuery value = Objects.requireNonNull(query, "query");
        return new TypeSelector(
                new SelectorDescription("types " + value.mode() + " annotated with "
                        + value.annotationType().binaryName()),
                (type, context) -> SemanticMatchers.typeAnnotation(type, value, context));
    }

    public static TypeSelector assignableTo(
            String targetBinaryName, UnknownHierarchyPolicy unknownPolicy) {
        JavaTypeName target = new JavaTypeName(targetBinaryName);
        UnknownHierarchyPolicy policy = Objects.requireNonNull(unknownPolicy, "unknownPolicy");
        return new TypeSelector(
                new SelectorDescription("types assignable to " + target.binaryName()
                        + " (unknown: " + policy + ")"),
                (type, context) -> SemanticMatchers.assignability(
                        type, target, true, policy, context));
    }

    public static TypeSelector assignableFrom(
            String sourceBinaryName, UnknownHierarchyPolicy unknownPolicy) {
        JavaTypeName source = new JavaTypeName(sourceBinaryName);
        UnknownHierarchyPolicy policy = Objects.requireNonNull(unknownPolicy, "unknownPolicy");
        return new TypeSelector(
                new SelectorDescription("types assignable from " + source.binaryName()
                        + " (unknown: " + policy + ")"),
                (type, context) -> SemanticMatchers.assignability(
                        type, source, false, policy, context));
    }

    public static TypeSelector inputKind(ClassFileInput.Kind kind) {
        ClassFileInput.Kind value = Objects.requireNonNull(kind, "kind");
        return new TypeSelector(
                new SelectorDescription("types from input kind " + value.name()),
                (type, context) -> type.location().resource().kind() == value);
    }

    public static TypeSelector container(String container) {
        String value = requireText(container, "container");
        return new TypeSelector(
                new SelectorDescription("types from container '" + value + "'"),
                (type, context) -> type.location().resource().container().equals(value));
    }

    public SelectorDescription description() {
        return description;
    }

    public SelectorConstant constant() {
        return constant;
    }

    public TypeSelector and(TypeSelector other) {
        return allOf(this, Objects.requireNonNull(other, "other"));
    }

    public TypeSelector or(TypeSelector other) {
        return anyOf(this, Objects.requireNonNull(other, "other"));
    }

    public TypeSelector not() {
        SelectorConstant negated = switch (constant) {
            case UNIVERSAL -> SelectorConstant.EMPTY;
            case EMPTY -> SelectorConstant.UNIVERSAL;
            case CONDITIONAL -> SelectorConstant.CONDITIONAL;
        };
        return new TypeSelector(
                SelectorDescriptions.not(description),
                (type, context) -> !matcher.matches(type, context),
                negated);
    }

    public TypeSelector excluding(TypeSelector exclusion) {
        TypeSelector value = Objects.requireNonNull(exclusion, "exclusion");
        SelectorConstant result = constant == SelectorConstant.EMPTY
                        || value.constant == SelectorConstant.UNIVERSAL
                ? SelectorConstant.EMPTY
                : value.constant == SelectorConstant.EMPTY ? constant : SelectorConstant.CONDITIONAL;
        Matcher combined = result == SelectorConstant.CONDITIONAL
                ? (type, context) -> matcher.matches(type, context)
                        && !value.matcher.matches(type, context)
                : (type, context) -> result == SelectorConstant.UNIVERSAL;
        return new TypeSelector(
                SelectorDescriptions.excluding(description, value.description),
                combined,
                result);
    }

    public boolean matches(JavaType type, Collection<JavaType> universe) {
        Objects.requireNonNull(type, "type");
        TypeSelectionContext context = new TypeSelectionContext(
                Objects.requireNonNull(universe, "universe"));
        return matcher.matches(type, context);
    }

    boolean matches(JavaType type, TypeSelectionContext context) {
        return matcher.matches(Objects.requireNonNull(type, "type"),
                Objects.requireNonNull(context, "context"));
    }

    public TypeSelection selectFrom(Collection<JavaType> types) {
        return select(types, List.of(), List.of());
    }

    public TypeSelection selectFrom(TypeModelResult model) {
        Objects.requireNonNull(model, "model");
        return select(model.types(), model.classFileDiagnostics(), model.diagnostics());
    }

    private TypeSelection select(
            Collection<JavaType> types,
            List<dev.archunitjava.importer.ClassFileDiagnostic> classFileDiagnostics,
            List<dev.archunitjava.model.TypeModelDiagnostic> modelDiagnostics) {
        Objects.requireNonNull(types, "types");
        List<JavaType> candidates = types.stream()
                .map(value -> Objects.requireNonNull(value, "type"))
                .distinct()
                .sorted()
                .toList();
        TypeSelectionContext context = new TypeSelectionContext(candidates);
        List<JavaType> selected = candidates.stream()
                .filter(type -> matcher.matches(type, context))
                .toList();
        return new TypeSelection(
                description,
                candidates.size(),
                selected,
                classFileDiagnostics,
                modelDiagnostics,
                List.copyOf(context.diagnostics()));
    }

    private static TypeSelector patternSelector(
            String attribute, JavaPattern pattern, Matcher matcher) {
        return new TypeSelector(
                new SelectorDescription(attribute + " matches " + pattern.description()), matcher);
    }

    private static List<TypeSelector> stable(
            Collection<TypeSelector> selectors, String operator) {
        Objects.requireNonNull(selectors, "selectors");
        List<TypeSelector> values = selectors.stream()
                .map(value -> Objects.requireNonNull(value, "selector"))
                .sorted(java.util.Comparator.comparing(value -> value.description.text()))
                .toList();
        if (values.isEmpty()) {
            throw new IllegalArgumentException(operator + " group must contain at least one selector");
        }
        return values;
    }

    private static List<SelectorDescription> descriptions(List<TypeSelector> selectors) {
        return selectors.stream().map(TypeSelector::description).toList();
    }

    private static JavaPattern qualified(JavaPattern pattern, String role) {
        return domain(pattern, PatternDomain.QUALIFIED_NAME, role);
    }

    private static JavaPattern resource(JavaPattern pattern, String role) {
        return domain(pattern, PatternDomain.RESOURCE_PATH, role);
    }

    private static JavaPattern domain(JavaPattern pattern, PatternDomain expected, String role) {
        JavaPattern value = Objects.requireNonNull(pattern, "pattern");
        if (value.description().domain() != expected) {
            throw new IllegalArgumentException(role + " requires " + expected + " pattern domain");
        }
        return value;
    }

    private static String requireText(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(JavaType type, TypeSelectionContext context);
    }
}
