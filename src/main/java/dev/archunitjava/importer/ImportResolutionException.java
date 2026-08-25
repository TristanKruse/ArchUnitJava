package dev.archunitjava.importer;

import java.util.Objects;

/** Policy-selected terminal packaging failure with no target exception leakage. */
public final class ImportResolutionException extends RuntimeException {
    private final ImportFailureKind kind;

    public ImportResolutionException(ImportFailureKind kind) {
        super("Static import failed: " + Objects.requireNonNull(kind, "kind"));
        this.kind = kind;
    }

    public ImportFailureKind kind() {
        return kind;
    }
}
