package dev.archunitjava.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/** Immutable declared field, method, constructor, or static initializer. */
public final class JavaMember implements Comparable<JavaMember> {
    private final JavaMemberSignature signature;
    private final JavaMemberKind kind;
    private final Set<JavaMemberModifier> modifiers;
    private final int accessFlags;
    private final int unrecognizedAccessFlags;
    private final boolean hasCode;
    private final DeclarationLocation location;
    private final LineNumberTable lineNumbers;
    private final List<JavaAnnotationOccurrence> annotations;
    private final Optional<JavaAnnotationValue> annotationDefault;
    private final Optional<GenericFieldView> genericFieldView;
    private final Optional<GenericMethodView> genericMethodView;
    private final List<JavaParameter> parameters;
    private final List<JavaCodeAccess> codeAccesses;
    private final List<JavaDynamicCallSite> dynamicCallSites;
    private final List<JavaExceptionEvidence> exceptionEvidence;

    JavaMember(
            JavaMemberSignature signature,
            JavaMemberKind kind,
            Set<JavaMemberModifier> modifiers,
            int accessFlags,
            int unrecognizedAccessFlags,
            boolean hasCode,
            DeclarationLocation location,
            LineNumberTable lineNumbers,
            List<JavaAnnotationOccurrence> annotations,
            Optional<JavaAnnotationValue> annotationDefault,
            Optional<GenericFieldView> genericFieldView,
            Optional<GenericMethodView> genericMethodView,
            List<JavaParameter> parameters,
            List<JavaCodeAccess> codeAccesses,
            List<JavaDynamicCallSite> dynamicCallSites,
            List<JavaExceptionEvidence> exceptionEvidence) {
        this.signature = Objects.requireNonNull(signature, "signature");
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(modifiers, "modifiers");
        EnumSet<JavaMemberModifier> copy = modifiers.isEmpty()
                ? EnumSet.noneOf(JavaMemberModifier.class)
                : EnumSet.copyOf(modifiers);
        this.modifiers = Collections.unmodifiableSet(copy);
        this.accessFlags = accessFlags;
        this.unrecognizedAccessFlags = unrecognizedAccessFlags;
        if (kind == JavaMemberKind.FIELD && hasCode) {
            throw new IllegalArgumentException("fields cannot contain bytecode");
        }
        this.hasCode = hasCode;
        this.location = Objects.requireNonNull(location, "location");
        this.lineNumbers = Objects.requireNonNull(lineNumbers, "lineNumbers");
        if (!hasCode && !lineNumbers.entries().isEmpty()) {
            throw new IllegalArgumentException("Members without bytecode cannot have line numbers");
        }
        Objects.requireNonNull(annotations, "annotations");
        this.annotations = annotations.stream()
                .map(value -> Objects.requireNonNull(value, "annotation"))
                .sorted()
                .toList();
        this.annotationDefault = Objects.requireNonNull(annotationDefault, "annotationDefault");
        this.genericFieldView = Objects.requireNonNull(genericFieldView, "genericFieldView");
        this.genericMethodView = Objects.requireNonNull(genericMethodView, "genericMethodView");
        if ((kind == JavaMemberKind.FIELD) != genericFieldView.isPresent()
                || (kind != JavaMemberKind.FIELD) != genericMethodView.isPresent()) {
            throw new IllegalArgumentException("Generic view must match the member kind");
        }
        Objects.requireNonNull(parameters, "parameters");
        this.parameters = parameters.stream()
                .map(value -> Objects.requireNonNull(value, "parameter"))
                .sorted()
                .toList();
        if (kind == JavaMemberKind.FIELD && !this.parameters.isEmpty()) {
            throw new IllegalArgumentException("Fields cannot have parameters");
        }
        if (this.parameters.stream().anyMatch(value -> !value.owner().equals(signature))) {
            throw new IllegalArgumentException("Parameter owner must match its member");
        }
        for (int index = 0; index < this.parameters.size(); index++) {
            if (this.parameters.get(index).index() != index) {
                throw new IllegalArgumentException("Parameter indexes must be contiguous from zero");
            }
        }
        Objects.requireNonNull(codeAccesses, "codeAccesses");
        this.codeAccesses = codeAccesses.stream()
                .map(value -> Objects.requireNonNull(value, "codeAccess"))
                .sorted()
                .toList();
        if (!hasCode && !this.codeAccesses.isEmpty()) {
            throw new IllegalArgumentException("Members without bytecode cannot have code accesses");
        }
        if (this.codeAccesses.stream().anyMatch(access -> !access.caller().equals(signature))) {
            throw new IllegalArgumentException("Code access caller must match its declaring member");
        }
        Objects.requireNonNull(dynamicCallSites, "dynamicCallSites");
        this.dynamicCallSites = dynamicCallSites.stream()
                .map(value -> Objects.requireNonNull(value, "dynamicCallSite"))
                .sorted()
                .toList();
        if (!hasCode && !this.dynamicCallSites.isEmpty()) {
            throw new IllegalArgumentException("Members without bytecode cannot have dynamic call sites");
        }
        if (this.dynamicCallSites.stream().anyMatch(site -> !site.caller().equals(signature))) {
            throw new IllegalArgumentException("Dynamic call-site caller must match its member");
        }
        Objects.requireNonNull(exceptionEvidence, "exceptionEvidence");
        this.exceptionEvidence = exceptionEvidence.stream()
                .map(value -> Objects.requireNonNull(value, "exceptionEvidence"))
                .sorted()
                .toList();
        if (kind == JavaMemberKind.FIELD && !this.exceptionEvidence.isEmpty()) {
            throw new IllegalArgumentException("Fields cannot have exception evidence");
        }
        if (this.exceptionEvidence.stream().anyMatch(value -> !value.owner().equals(signature))) {
            throw new IllegalArgumentException("Exception evidence owner must match its member");
        }
    }

    public JavaMemberSignature signature() {
        return signature;
    }

    public JavaTypeName owner() {
        return signature.owner();
    }

    public String name() {
        return signature.name();
    }

    public String descriptor() {
        return signature.descriptor();
    }

    public JavaMemberKind kind() {
        return kind;
    }

    public Set<JavaMemberModifier> modifiers() {
        return modifiers;
    }

    public int accessFlags() {
        return accessFlags;
    }

    public int unrecognizedAccessFlags() {
        return unrecognizedAccessFlags;
    }

    public boolean hasCode() {
        return hasCode;
    }

    public boolean isCodeUnit() {
        return kind != JavaMemberKind.FIELD;
    }

    public DeclarationLocation location() {
        return location;
    }

    public LineNumberTable lineNumbers() {
        return lineNumbers;
    }

    public BytecodeLocation bytecodeLocation(int bytecodeOffset) {
        if (!hasCode) throw new IllegalStateException("Member has no bytecode");
        return new BytecodeLocation(
                location.resource(),
                location.sourceFile(),
                bytecodeOffset,
                lineNumbers.lineAt(bytecodeOffset));
    }

    public List<JavaAnnotationOccurrence> annotations() {
        return annotations;
    }

    public Optional<JavaAnnotationValue> annotationDefault() {
        return annotationDefault;
    }

    public JvmType fieldType() {
        if (kind != JavaMemberKind.FIELD) {
            throw new IllegalStateException("Only fields have a field type");
        }
        return genericFieldView.orElseThrow().erasedType();
    }

    public JvmMethodType methodType() {
        if (!isCodeUnit()) {
            throw new IllegalStateException("Fields do not have a method type");
        }
        return genericMethodView.orElseThrow().erasedType();
    }

    public GenericFieldView genericFieldView() {
        if (kind != JavaMemberKind.FIELD) {
            throw new IllegalStateException("Only fields have a generic field view");
        }
        return genericFieldView.orElseThrow();
    }

    public GenericMethodView genericMethodView() {
        if (!isCodeUnit()) {
            throw new IllegalStateException("Fields do not have a generic method view");
        }
        return genericMethodView.orElseThrow();
    }

    public List<JavaParameter> parameters() {
        return parameters;
    }

    public List<JavaCodeAccess> codeAccesses() {
        return codeAccesses;
    }

    public List<JavaDynamicCallSite> dynamicCallSites() {
        return dynamicCallSites;
    }

    public List<JavaExceptionEvidence> exceptionEvidence() {
        return exceptionEvidence;
    }

    @Override
    public int compareTo(JavaMember other) {
        return signature.compareTo(other.signature);
    }
}
