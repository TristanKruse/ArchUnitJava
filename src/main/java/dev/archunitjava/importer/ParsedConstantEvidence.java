package dev.archunitjava.importer;

import java.util.Objects;
import java.util.Optional;

/** Backend-neutral typed evidence for a relevant constant-pool entry. */
public record ParsedConstantEvidence(
        Kind kind,
        int constantPoolIndex,
        String descriptor,
        Optional<ParsedMethodHandle> methodHandle,
        Optional<ParsedDynamicConstant> dynamicConstant,
        Optional<ParsedConstantLoadSite> loadSite)
        implements Comparable<ParsedConstantEvidence> {
    public enum Kind {
        CLASS_LITERAL,
        METHOD_TYPE,
        METHOD_HANDLE,
        DYNAMIC_CONSTANT
    }

    public ParsedConstantEvidence {
        Objects.requireNonNull(kind, "kind");
        if (constantPoolIndex < 1) throw new IllegalArgumentException("constantPoolIndex must be positive");
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
        Objects.requireNonNull(methodHandle, "methodHandle");
        Objects.requireNonNull(dynamicConstant, "dynamicConstant");
        Objects.requireNonNull(loadSite, "loadSite");
        if ((kind == Kind.METHOD_HANDLE) != methodHandle.isPresent()) {
            throw new IllegalArgumentException("Method-handle structure does not match evidence kind");
        }
        if ((kind == Kind.DYNAMIC_CONSTANT) != dynamicConstant.isPresent()) {
            throw new IllegalArgumentException("Dynamic-constant structure does not match evidence kind");
        }
        if ((kind == Kind.CLASS_LITERAL) != loadSite.isPresent()) {
            throw new IllegalArgumentException("Only an observed class literal has a load site");
        }
    }

    @Override
    public int compareTo(ParsedConstantEvidence other) {
        int result = Integer.compare(constantPoolIndex, other.constantPoolIndex);
        if (result != 0) return result;
        result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = descriptor.compareTo(other.descriptor);
        return result != 0 ? result : loadSite.map(ParsedConstantLoadSite::toString).orElse("")
                .compareTo(other.loadSite.map(ParsedConstantLoadSite::toString).orElse(""));
    }
}
