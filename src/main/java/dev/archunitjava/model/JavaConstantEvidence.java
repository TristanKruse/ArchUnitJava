package dev.archunitjava.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed, unresolved evidence for a relevant constant-pool entry. */
public record JavaConstantEvidence(
        JavaConstantEvidenceKind kind,
        int constantPoolIndex,
        String descriptor,
        List<JvmType> referencedTypes,
        Optional<JavaMethodHandle> methodHandle,
        Optional<JavaDynamicConstant> dynamicConstant,
        Optional<JavaConstantLoadSite> loadSite)
        implements Comparable<JavaConstantEvidence> {
    public JavaConstantEvidence {
        Objects.requireNonNull(kind, "kind");
        if (constantPoolIndex < 1) throw new IllegalArgumentException("constantPoolIndex must be positive");
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
        Objects.requireNonNull(referencedTypes, "referencedTypes");
        referencedTypes = referencedTypes.stream()
                .map(value -> Objects.requireNonNull(value, "referencedType"))
                .distinct()
                .sorted(Comparator.comparing(JvmType::descriptor))
                .toList();
        Objects.requireNonNull(methodHandle, "methodHandle");
        Objects.requireNonNull(dynamicConstant, "dynamicConstant");
        Objects.requireNonNull(loadSite, "loadSite");
        if ((kind == JavaConstantEvidenceKind.METHOD_HANDLE) != methodHandle.isPresent()) {
            throw new IllegalArgumentException("Method-handle structure does not match evidence kind");
        }
        if ((kind == JavaConstantEvidenceKind.DYNAMIC_CONSTANT) != dynamicConstant.isPresent()) {
            throw new IllegalArgumentException("Dynamic-constant structure does not match evidence kind");
        }
        if ((kind == JavaConstantEvidenceKind.CLASS_LITERAL) != loadSite.isPresent()) {
            throw new IllegalArgumentException("Only an observed class literal has a load site");
        }
    }

    @Override
    public int compareTo(JavaConstantEvidence other) {
        int result = Integer.compare(constantPoolIndex, other.constantPoolIndex);
        if (result != 0) return result;
        result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = descriptor.compareTo(other.descriptor);
        return result != 0 ? result : loadSite.map(JavaConstantLoadSite::toString).orElse("")
                .compareTo(other.loadSite.map(JavaConstantLoadSite::toString).orElse(""));
    }
}
