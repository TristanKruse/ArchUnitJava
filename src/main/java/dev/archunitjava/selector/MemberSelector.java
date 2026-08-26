package dev.archunitjava.selector;

import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberKind;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaMemberSignature;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JvmDescriptors;
import dev.archunitjava.model.JvmMethodType;
import dev.archunitjava.model.JvmType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Immutable selectors for fields, methods, constructors, and static initializers. */
public final class MemberSelector {
    private final SelectorDescription description;
    private final Matcher matcher;

    private MemberSelector(SelectorDescription description, Matcher matcher) {
        this.description = Objects.requireNonNull(description, "description");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
    }

    public static MemberSelector all() {
        return new MemberSelector(
                new SelectorDescription("all declared members"), (member, context) -> true);
    }

    public static MemberSelector fields() {
        return kind(JavaMemberKind.FIELD, "fields");
    }

    public static MemberSelector methods() {
        return kind(JavaMemberKind.METHOD, "methods");
    }

    public static MemberSelector constructors() {
        return kind(JavaMemberKind.CONSTRUCTOR, "constructors");
    }

    public static MemberSelector staticInitializers() {
        return kind(JavaMemberKind.STATIC_INITIALIZER, "static initializers");
    }

    public static MemberSelector codeUnits() {
        return new MemberSelector(
                new SelectorDescription("code units"),
                (member, context) -> member.isCodeUnit());
    }

    public static MemberSelector visibility(MemberVisibility visibility) {
        MemberVisibility value = Objects.requireNonNull(visibility, "visibility");
        return new MemberSelector(
                new SelectorDescription("members with " + value.name() + " visibility"),
                (member, context) -> switch (value) {
                    case PUBLIC -> member.modifiers().contains(JavaMemberModifier.PUBLIC);
                    case PROTECTED -> member.modifiers().contains(JavaMemberModifier.PROTECTED);
                    case PRIVATE -> member.modifiers().contains(JavaMemberModifier.PRIVATE);
                    case PACKAGE_PRIVATE -> member.modifiers().stream().noneMatch(modifier ->
                            modifier == JavaMemberModifier.PUBLIC
                                    || modifier == JavaMemberModifier.PROTECTED
                                    || modifier == JavaMemberModifier.PRIVATE);
                });
    }

    public static MemberSelector modifier(JavaMemberModifier modifier) {
        JavaMemberModifier value = Objects.requireNonNull(modifier, "modifier");
        return new MemberSelector(
                new SelectorDescription("members with modifier " + value.name()),
                (member, context) -> member.modifiers().contains(value));
    }

    public static MemberSelector annotatedWith(AnnotationQuery query) {
        AnnotationQuery value = Objects.requireNonNull(query, "query");
        if (value.mode() == AnnotationMatchMode.INHERITED_DECLARATION) {
            throw new IllegalArgumentException("Member annotations cannot use inherited mode");
        }
        return new MemberSelector(
                new SelectorDescription("members " + value.mode() + " annotated with "
                        + value.annotationType().binaryName()),
                (member, context) -> SemanticMatchers.memberAnnotation(
                        member, value, context.typeContext()));
    }

    public static MemberSelector named(String name) {
        String value = requireText(name, "name");
        return new MemberSelector(
                new SelectorDescription("members named '" + value + "'"),
                (member, context) -> member.name().equals(value));
    }

    public static MemberSelector name(JavaPattern pattern) {
        JavaPattern value = Objects.requireNonNull(pattern, "pattern");
        if (value.description().domain() != PatternDomain.QUALIFIED_NAME) {
            throw new IllegalArgumentException("Member name requires QUALIFIED_NAME pattern domain");
        }
        return new MemberSelector(
                new SelectorDescription("member name matches " + value.description()),
                (member, context) -> value.matches(member.name()));
    }

    /** Exact JVM field or method descriptor; overloads remain unambiguous. */
    public static MemberSelector descriptor(String descriptor) {
        String value = validatedDescriptor(descriptor);
        return new MemberSelector(
                new SelectorDescription("members with descriptor '" + value + "'"),
                (member, context) -> member.descriptor().equals(value));
    }

    public static MemberSelector signature(JavaMemberSignature signature) {
        JavaMemberSignature value = Objects.requireNonNull(signature, "signature");
        return new MemberSelector(
                new SelectorDescription("member " + value.stableKey()),
                (member, context) -> member.signature().equals(value));
    }

    public static MemberSelector parameterTypes(List<JvmType> parameterTypes) {
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        List<JvmType> value = parameterTypes.stream()
                .map(type -> Objects.requireNonNull(type, "parameterType"))
                .toList();
        // Reuse the model invariant to reject void parameters.
        new JvmMethodType(value, dev.archunitjava.model.JvmVoidType.VOID);
        String descriptors = value.stream().map(JvmType::descriptor).toList().toString();
        return new MemberSelector(
                new SelectorDescription("code units with parameter descriptors " + descriptors),
                (member, context) -> member.isCodeUnit()
                        && member.methodType().parameterTypes().equals(value));
    }

    public static MemberSelector parameterTypes(JvmType... parameterTypes) {
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        return parameterTypes(Arrays.asList(parameterTypes.clone()));
    }

    public static MemberSelector returnType(JvmType returnType) {
        JvmType value = Objects.requireNonNull(returnType, "returnType");
        return new MemberSelector(
                new SelectorDescription("code units returning " + value.descriptor()),
                (member, context) -> member.isCodeUnit()
                        && member.methodType().returnType().equals(value));
    }

    public static MemberSelector fieldType(JvmType fieldType) {
        JvmType value = Objects.requireNonNull(fieldType, "fieldType");
        if (value instanceof dev.archunitjava.model.JvmVoidType) {
            throw new IllegalArgumentException("Field type cannot be void");
        }
        return new MemberSelector(
                new SelectorDescription("fields of type " + value.descriptor()),
                (member, context) -> member.kind() == JavaMemberKind.FIELD
                        && member.fieldType().equals(value));
    }

    public static MemberSelector declaredBy(TypeSelector selector) {
        TypeSelector value = Objects.requireNonNull(selector, "selector");
        return new MemberSelector(
                new SelectorDescription("members declared by " + value.description()),
                (member, context) -> context.owner(member)
                        .filter(type -> value.matches(type, context.typeContext()))
                        .isPresent());
    }

    public static MemberSelector declaredIn(PackageSelector selector) {
        PackageSelector value = Objects.requireNonNull(selector, "selector");
        return new MemberSelector(
                new SelectorDescription("members declared in " + value.description()),
                (member, context) -> context.ownerPackage(member)
                        .filter(pkg -> value.matches(pkg, context.typeContext()))
                        .isPresent());
    }

    public SelectorDescription description() {
        return description;
    }

    public MemberSelection selectFrom(Collection<JavaType> types) {
        return select(types, List.of(), List.of(), List.of());
    }

    public MemberSelection selectFrom(TypeModelResult model) {
        Objects.requireNonNull(model, "model");
        return select(
                model.types(),
                model.classFileDiagnostics(),
                model.diagnostics(),
                List.of());
    }

    public MemberSelection selectFrom(TypeSelection selection) {
        Objects.requireNonNull(selection, "selection");
        return select(
                selection.selected(),
                selection.classFileDiagnostics(),
                selection.modelDiagnostics(),
                selection.selectionDiagnostics());
    }

    private MemberSelection select(
            Collection<JavaType> types,
            List<dev.archunitjava.importer.ClassFileDiagnostic> classFileDiagnostics,
            List<dev.archunitjava.model.TypeModelDiagnostic> modelDiagnostics,
            List<SelectionDiagnostic> inheritedDiagnostics) {
        List<JavaType> universe = types.stream()
                .map(value -> Objects.requireNonNull(value, "type"))
                .distinct()
                .sorted()
                .toList();
        List<JavaMember> candidates = universe.stream()
                .map(JavaType::declaredMembers)
                .flatMap(Collection::stream)
                .distinct()
                .sorted()
                .toList();
        MemberSelectionContext context = new MemberSelectionContext(universe);
        List<JavaMember> selected = candidates.stream()
                .filter(member -> matcher.matches(member, context))
                .toList();
        List<SelectionDiagnostic> diagnostics = java.util.stream.Stream.concat(
                        inheritedDiagnostics.stream(), context.diagnostics().stream())
                .distinct()
                .sorted()
                .toList();
        return new MemberSelection(
                description,
                candidates.size(),
                selected,
                classFileDiagnostics,
                modelDiagnostics,
                diagnostics);
    }

    private static MemberSelector kind(JavaMemberKind kind, String description) {
        return new MemberSelector(
                new SelectorDescription(description),
                (member, context) -> member.kind() == kind);
    }

    private static String validatedDescriptor(String descriptor) {
        String value = requireText(descriptor, "descriptor");
        try {
            JvmDescriptors.parseField(value);
            return value;
        } catch (RuntimeException fieldFailure) {
            try {
                JvmDescriptors.parseMethod(value);
                return value;
            } catch (RuntimeException methodFailure) {
                IllegalArgumentException result = new IllegalArgumentException(
                        "Invalid JVM member descriptor: " + value, methodFailure);
                result.addSuppressed(fieldFailure);
                throw result;
            }
        }
    }

    private static String requireText(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }

    @FunctionalInterface
    private interface Matcher {
        boolean matches(JavaMember member, MemberSelectionContext context);
    }
}
