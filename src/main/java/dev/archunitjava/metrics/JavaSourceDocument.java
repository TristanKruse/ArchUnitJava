package dev.archunitjava.metrics;

import java.util.Objects;

/** Caller-supplied source text; source files are never located or executed implicitly. */
public record JavaSourceDocument(SourceDocumentId id, String content, boolean generated) {
    public JavaSourceDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(content, "content");
    }
}
