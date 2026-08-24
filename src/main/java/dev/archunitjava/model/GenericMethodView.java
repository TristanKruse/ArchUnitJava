package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Erased method type plus independently parsed optional generic evidence. */
public record GenericMethodView(
        JvmMethodType erasedType,
        Optional<String> declaredSignature,
        Optional<GenericMethodSignature> genericSignature,
        List<GenericSignatureDiagnostic> diagnostics) {
    public GenericMethodView {
        Objects.requireNonNull(erasedType, "erasedType");
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

    static GenericMethodView create(
            JvmMethodType erasedType, Optional<String> declaredSignature) {
        try {
            return new GenericMethodView(
                    erasedType,
                    declaredSignature,
                    declaredSignature.map(GenericSignatures::parseMethod),
                    List.of());
        } catch (InvalidGenericSignatureException failure) {
            return new GenericMethodView(
                    erasedType,
                    declaredSignature,
                    Optional.empty(),
                    List.of(GenericSignatureDiagnostic.from(failure)));
        }
    }

    public boolean usesErasedFallback() {
        return genericSignature.isEmpty();
    }

    public List<JvmReferenceType> referencedTypes(TypeReferenceEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        java.util.ArrayList<JvmType> erasedTypes = new java.util.ArrayList<>(
                erasedType.parameterTypes());
        erasedTypes.add(erasedType.returnType());
        List<JvmReferenceType> erased = TypeReferences.fromJvmTypes(erasedTypes);
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
