package dev.archunitjava.model;

import java.util.Objects;
import java.util.Optional;

/** Resource and optional source-file location for a type or declared member. */
public record DeclarationLocation(
        ClassResourceLocation resource, Optional<SourceFileName> sourceFile) {
    public DeclarationLocation {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(sourceFile, "sourceFile");
    }
}
