package dev.archunitjava.baseline;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Reviewed, rationalized suppression with composable exact scope and optional expiry. */
public record Suppression(
        String id,
        String rationale,
        Optional<String> ruleId,
        Optional<String> subjectId,
        Optional<String> evidenceLocation,
        Optional<LocalDate> expiresOn) implements Comparable<Suppression> {
    public Suppression {
        id = requireText(id, "id");
        rationale = requireText(rationale, "rationale");
        ruleId = textOptional(ruleId, "ruleId");
        subjectId = textOptional(subjectId, "subjectId");
        evidenceLocation = textOptional(evidenceLocation, "evidenceLocation");
        Objects.requireNonNull(expiresOn, "expiresOn");
        if (ruleId.isEmpty() && subjectId.isEmpty() && evidenceLocation.isEmpty()) {
            throw new IllegalArgumentException(
                    "A suppression must be scoped by rule, subject, or evidence");
        }
    }

    public static Suppression forRule(
            String id, String rationale, String ruleId, Optional<LocalDate> expiresOn) {
        return new Suppression(id, rationale, Optional.of(ruleId), Optional.empty(),
                Optional.empty(), expiresOn);
    }

    public boolean matches(BaselineFinding finding) {
        Objects.requireNonNull(finding, "finding");
        return ruleId.map(value -> value.equals(finding.ruleId())).orElse(true)
                && subjectId.map(finding.subjectIds()::contains).orElse(true)
                && evidenceLocation.map(finding.evidenceLocations()::contains).orElse(true);
    }

    /** Expiry dates are inclusive: the suppression is valid through the named date. */
    public boolean expiredOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return expiresOn.map(expiry -> expiry.isBefore(date)).orElse(false);
    }

    @Override
    public int compareTo(Suppression other) {
        return id.compareTo(other.id);
    }

    private static Optional<String> textOptional(Optional<String> value, String role) {
        Objects.requireNonNull(value, role);
        return value.map(item -> requireText(item, role));
    }

    private static String requireText(String value, String role) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value;
    }
}
