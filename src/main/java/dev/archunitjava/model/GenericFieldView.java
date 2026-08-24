package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Erased field type plus independently parsed optional generic evidence. */
public record GenericFieldView(
        JvmType erasedType,
        Optional<String> declaredSignature,
        Optional<GenericType> genericType,
        List<GenericSignatureDiagnostic> diagnostics) {
    public GenericFieldView {
        Objects.requireNonNull(erasedType, "erasedType");
        if (erasedType instanceof JvmVoidType) throw new IllegalArgumentException("Fields cannot be void");
        Objects.requireNonNull(declaredSignature, "declaredSignature");
        Objects.requireNonNull(genericType, "genericType");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = diagnostics.stream().sorted().toList();
        if (declaredSignature.isEmpty() && (genericType.isPresent() || !diagnostics.isEmpty())) {
            throw new IllegalArgumentException("Generic evidence requires a declared signature");
        }
        if (declaredSignature.isPresent()
                && genericType.isPresent() == !diagnostics.isEmpty()) {
            throw new IllegalArgumentException("A declared signature is either parsed or diagnosed");
        }
    }

    static GenericFieldView create(JvmType erasedType, Optional<String> declaredSignature) {
        try {
            return new GenericFieldView(
                    erasedType,
                    declaredSignature,
                    declaredSignature.map(GenericSignatures::parseField),
                    List.of());
        } catch (InvalidGenericSignatureException failure) {
            return new GenericFieldView(
                    erasedType,
                    declaredSignature,
                    Optional.empty(),
                    List.of(GenericSignatureDiagnostic.from(failure)));
        }
    }

    public boolean usesErasedFallback() {
        return genericType.isEmpty();
    }

    public List<JvmReferenceType> referencedTypes(TypeReferenceEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        List<JvmReferenceType> erased = TypeReferences.fromJvmTypes(List.of(erasedType));
        List<JvmReferenceType> generic = genericType.stream()
                .flatMap(value -> value.referencedTypes().stream())
                .toList();
        return switch (evidence) {
            case ERASED -> erased;
            case GENERIC -> generic;
            case COMBINED -> TypeReferences.merge(erased, generic);
        };
    }
}
