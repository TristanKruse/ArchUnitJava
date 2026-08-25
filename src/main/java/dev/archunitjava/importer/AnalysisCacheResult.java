package dev.archunitjava.importer;

import java.util.Objects;

/** Opaque deterministic model bytes and whether they came from a validated cache entry. */
public record AnalysisCacheResult(byte[] payload, AnalysisCacheStatus status) {
    public AnalysisCacheResult {
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
        Objects.requireNonNull(status, "status");
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
