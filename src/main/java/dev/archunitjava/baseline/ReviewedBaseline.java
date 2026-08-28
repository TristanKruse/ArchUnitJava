package dev.archunitjava.baseline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable reviewed baseline; creation and updates are explicit operations. */
public record ReviewedBaseline(
        String schemaVersion, List<BaselineFinding> findings, List<Suppression> suppressions) {
    public static final String CURRENT_SCHEMA_VERSION = "archunitjava.baseline.v1";

    public ReviewedBaseline {
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported baseline schema: " + schemaVersion);
        }
        Objects.requireNonNull(findings, "findings");
        TreeMap<String, BaselineFinding> byExactFingerprint = new TreeMap<>();
        for (BaselineFinding finding : findings) {
            Objects.requireNonNull(finding, "finding");
            BaselineFinding previous = byExactFingerprint.putIfAbsent(
                    finding.exactFingerprint(), finding);
            if (previous != null && !previous.equals(finding)) {
                throw new IllegalArgumentException(
                        "Baseline fingerprint collision: " + finding.exactFingerprint());
            }
        }
        ArrayList<BaselineFinding> normalizedFindings = new ArrayList<>(byExactFingerprint.values());
        normalizedFindings.sort(null);
        findings = List.copyOf(normalizedFindings);

        Objects.requireNonNull(suppressions, "suppressions");
        TreeMap<String, Suppression> byId = new TreeMap<>();
        for (Suppression suppression : suppressions) {
            Objects.requireNonNull(suppression, "suppression");
            if (byId.putIfAbsent(suppression.id(), suppression) != null) {
                throw new IllegalArgumentException("Duplicate suppression id: " + suppression.id());
            }
        }
        suppressions = List.copyOf(byId.values());
    }

    public static ReviewedBaseline of(
            Collection<BaselineFinding> findings, Collection<Suppression> suppressions) {
        return new ReviewedBaseline(
                CURRENT_SCHEMA_VERSION, List.copyOf(findings), List.copyOf(suppressions));
    }
}
