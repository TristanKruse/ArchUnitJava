package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Erased class hierarchy plus independently parsed optional generic evidence. */
public record GenericClassView(
        Optional<JvmReferenceType> erasedSuperclass,
        List<JvmReferenceType> erasedInterfaces,
        Optional<String> declaredSignature,
        Optional<GenericClassSignature> genericSignature,
        List<GenericSignatureDiagnostic> diagnostics) {
    public GenericClassView {
        Objects.requireNonNull(erasedSuperclass, "erasedSuperclass");
        Objects.requireNonNull(erasedInterfaces, "erasedInterfaces");
        erasedInterfaces = List.copyOf(erasedInterfaces);
        erasedInterfaces.forEach(value -> Objects.requireNonNull(value, "erasedInterface"));
        Objects.requireNonNull(declaredSignature, "declaredSignature");
        Objects.requireNonNull(genericSignature, "genericSignature");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = diagnostics.stream().sorted().toList();
        if (declaredSignature.isEmpty() && (genericSignature.isPresent() || !diagnostics.isEmpty())) {
            throw new IllegalArgumentException("Generic evidence requires a declared signature");
        }
        if (declaredSignature.isPresent()
                && genericSignature.isPresent() == !diagnostics.isEmpty()) {
            throw new IllegalArgumentException("A declared signature is either parsed or diagnosed");
        }
    }

    static GenericClassView create(
            Optional<JvmReferenceType> erasedSuperclass,
            List<JvmReferenceType> erasedInterfaces,
            Optional<String> declaredSignature) {
        try {
            return new GenericClassView(
                    erasedSuperclass,
                    erasedInterfaces,
                    declaredSignature,
                    declaredSignature.map(GenericSignatures::parseClass),
                    List.of());
        } catch (InvalidGenericSignatureException failure) {
            return new GenericClassView(
                    erasedSuperclass,
                    erasedInterfaces,
                    declaredSignature,
                    Optional.empty(),
                    List.of(GenericSignatureDiagnostic.from(failure)));
        }
    }

    /** True when queries must rely on the descriptor/class-header view. */
    public boolean usesErasedFallback() {
        return genericSignature.isEmpty();
    }

    public List<JvmReferenceType> referencedTypes(TypeReferenceEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        List<JvmReferenceType> erased = TypeReferences.merge(
                erasedSuperclass.stream().toList(), erasedInterfaces);
        List<JvmReferenceType> generic = genericSignature.stream()
                .flatMap(value -> value.referencedTypes().stream())
                .toList();
        return switch (evidence) {
            case ERASED -> erased;
            case GENERIC -> generic;
            case COMBINED -> TypeReferences.merge(erased, generic);
        };
    }
}
