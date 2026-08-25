package dev.archunitjava.importer;

import java.io.IOException;

/** Trusted library-side production of a deterministic imported-model payload. */
@FunctionalInterface
public interface AnalysisPayloadSupplier {
    byte[] get() throws IOException;
}
