package dev.archunitjava.baseline;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reviewable snapshot and two-part stable fingerprint for one rule violation. */
public record BaselineFinding(
        String identityFingerprint,
        String evidenceFingerprint,
        String exactFingerprint,
        String ruleId,
        String violationId,
        String violationCode,
        String severity,
        List<String> subjects,
        List<String> subjectIds,
        List<String> evidenceLocations) implements Comparable<BaselineFinding> {
    public BaselineFinding {
        identityFingerprint = FingerprintHash.require(identityFingerprint, "identityFingerprint");
        evidenceFingerprint = FingerprintHash.require(evidenceFingerprint, "evidenceFingerprint");
        exactFingerprint = FingerprintHash.require(exactFingerprint, "exactFingerprint");
        ruleId = requireText(ruleId, "ruleId");
        violationId = requireText(violationId, "violationId");
        violationCode = requireText(violationCode, "violationCode");
        severity = requireText(severity, "severity");
        subjects = sortedDistinct(subjects, "subjects");
        subjectIds = sortedDistinct(subjectIds, "subjectIds");
        evidenceLocations = sortedDistinct(evidenceLocations, "evidenceLocations");
        if (subjects.isEmpty() || subjectIds.isEmpty()) {
            throw new IllegalArgumentException("A baseline finding requires a subject");
        }
        String expected = new FingerprintHash().add(identityFingerprint)
                .add(evidenceFingerprint).finish();
        if (!exactFingerprint.equals(expected)) {
            throw new IllegalArgumentException("exactFingerprint does not match finding parts");
        }
    }

    public static BaselineFinding capture(RuleResult result, Violation violation) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(violation, "violation");
        FingerprintHash identity = new FingerprintHash()
                .add(result.ruleId())
                .add(violation.id().value())
                .add(violation.code())
                .add(violation.severity().name());
        List<String> subjects = new ArrayList<>();
        List<String> subjectIds = new ArrayList<>();
        violation.subjects().forEach(subject -> {
            subjects.add(subject.role() + "=" + subject.id().stableKey());
            subjectIds.add(subject.id().stableKey());
            identity.add(subject.role()).add(subject.id().stableKey());
        });
        violation.attributes().forEach((key, value) -> identity.add(key).add(value));

        FingerprintHash evidence = new FingerprintHash();
        List<String> locations = new ArrayList<>();
        for (DependencyEvidence item : violation.evidence()) {
            locations.add(item.location().resourcePath());
            evidence.add(item.location().resourcePath())
                    .add(item.ownerMember().map(member -> member.stableKey()).orElse(""))
                    .add(item.bytecodeOffset().isPresent()
                            ? Integer.toString(item.bytecodeOffset().getAsInt()) : "")
                    .add(item.sourceFile().orElse(""))
                    .add(item.lineNumber().isPresent()
                            ? Integer.toString(item.lineNumber().getAsInt()) : "");
        }
        String identityValue = identity.finish();
        String evidenceValue = evidence.finish();
        return new BaselineFinding(
                identityValue,
                evidenceValue,
                new FingerprintHash().add(identityValue).add(evidenceValue).finish(),
                result.ruleId(),
                violation.id().value(),
                violation.code(),
                violation.severity().name(),
                subjects,
                subjectIds,
                locations);
    }

    @Override
    public int compareTo(BaselineFinding other) {
        int result = identityFingerprint.compareTo(other.identityFingerprint);
        if (result != 0) return result;
        result = evidenceFingerprint.compareTo(other.evidenceFingerprint);
        return result != 0 ? result : exactFingerprint.compareTo(other.exactFingerprint);
    }

    private static List<String> sortedDistinct(List<String> values, String role) {
        Objects.requireNonNull(values, role);
        java.util.TreeSet<String> sorted = new java.util.TreeSet<>();
        values.forEach(value -> sorted.add(requireText(value, role + " item")));
        return List.copyOf(sorted);
    }

    private static String requireText(String value, String role) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value;
    }
}
