package dev.archunitjava.result;

import dev.archunitjava.graph.DependencyEvidence;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Structured architecture-rule failure data, with no renderer-owned prose. */
public record Violation(
        ViolationId id,
        String code,
        Severity severity,
        List<ViolationSubject> subjects,
        List<DependencyEvidence> evidence,
        Map<String, String> attributes) implements Comparable<Violation> {
    public Violation {
        Objects.requireNonNull(id, "id");
        code = ResultValues.requireText(code, "violation code");
        Objects.requireNonNull(severity, "severity");
        subjects = ResultValues.sortedDistinct(subjects, "subjects");
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("Violation must identify at least one subject");
        }
        evidence = ResultValues.sortedDistinct(evidence, "evidence");
        attributes = ResultValues.sortedTextMap(attributes, "violation attributes");
    }

    @Override
    public int compareTo(Violation other) {
        int result = id.compareTo(other.id);
        if (result != 0) return result;
        result = code.compareTo(other.code);
        if (result != 0) return result;
        result = severity.compareTo(other.severity);
        if (result != 0) return result;
        result = ResultValues.compareLists(subjects, other.subjects);
        if (result != 0) return result;
        result = ResultValues.compareLists(evidence, other.evidence);
        return result != 0 ? result : ResultValues.compareMaps(attributes, other.attributes);
    }
}
