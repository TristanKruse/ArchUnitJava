package dev.archunitjava.metrics;

import dev.archunitjava.graph.StableId;
import dev.archunitjava.model.JavaPackageName;
import dev.archunitjava.model.SourceFileName;
import java.util.Objects;

/** Package-qualified source identity; a bare SourceFile attribute is never treated as a path. */
public record SourceDocumentId(JavaPackageName packageName, SourceFileName fileName)
        implements StableId {
    public SourceDocumentId {
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(fileName, "fileName");
    }

    @Override
    public String stableKey() {
        return "source:" + (packageName.isUnnamed() ? "<unnamed>" : packageName.value())
                + '/' + fileName.value();
    }
}
