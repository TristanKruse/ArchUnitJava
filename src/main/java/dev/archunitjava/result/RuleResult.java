package dev.archunitjava.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable result of one rule evaluation, with an explicit non-overlapping terminal state. */
public final class RuleResult implements Comparable<RuleResult> {
    private final String ruleId;
    private final RuleStatus status;
    private final List<Violation> violations;
    private final List<Diagnostic> diagnostics;

    private RuleResult(
            String ruleId,
            RuleStatus status,
            List<Violation> violations,
            List<Diagnostic> diagnostics) {
        this.ruleId = ResultValues.requireText(ruleId, "rule id");
        this.status = Objects.requireNonNull(status, "status");
        this.violations = normalizeViolations(violations);
        this.diagnostics = ResultValues.sortedDistinct(diagnostics, "diagnostics");
        validateState();
    }

    public static RuleResult passed(String ruleId) {
        return passed(ruleId, List.of());
    }

    public static RuleResult passed(String ruleId, List<Diagnostic> diagnostics) {
        return new RuleResult(ruleId, RuleStatus.PASSED, List.of(), diagnostics);
    }

    public static RuleResult failed(
            String ruleId, List<Violation> violations, List<Diagnostic> diagnostics) {
        return new RuleResult(ruleId, RuleStatus.FAILED, violations, diagnostics);
    }

    public static RuleResult skipped(String ruleId, List<Diagnostic> diagnostics) {
        return new RuleResult(ruleId, RuleStatus.SKIPPED, List.of(), diagnostics);
    }

    public static RuleResult incomplete(
            String ruleId, List<Violation> violations, List<Diagnostic> diagnostics) {
        return new RuleResult(ruleId, RuleStatus.INCOMPLETE, violations, diagnostics);
    }

    public String ruleId() {
        return ruleId;
    }

    public RuleStatus status() {
        return status;
    }

    public List<Violation> violations() {
        return violations;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public boolean passed() {
        return status == RuleStatus.PASSED;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RuleResult result
                && ruleId.equals(result.ruleId)
                && status == result.status
                && violations.equals(result.violations)
                && diagnostics.equals(result.diagnostics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId, status, violations, diagnostics);
    }

    @Override
    public int compareTo(RuleResult other) {
        int result = ruleId.compareTo(other.ruleId);
        if (result != 0) return result;
        result = status.compareTo(other.status);
        if (result != 0) return result;
        result = ResultValues.compareLists(violations, other.violations);
        return result != 0 ? result : ResultValues.compareLists(diagnostics, other.diagnostics);
    }

    @Override
    public String toString() {
        return "RuleResult[ruleId=" + ruleId + ", status=" + status + ", violations="
                + violations.size() + ", diagnostics=" + diagnostics.size() + ']';
    }

    private List<Violation> normalizeViolations(List<Violation> values) {
        Objects.requireNonNull(values, "violations");
        Map<ViolationId, Violation> byId = new TreeMap<>();
        for (Violation value : values) {
            Objects.requireNonNull(value, "violation");
            Violation previous = byId.putIfAbsent(value.id(), value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalArgumentException("Violation id collision: " + value.id().value());
            }
        }
        return List.copyOf(new ArrayList<>(byId.values()));
    }

    private void validateState() {
        if (status == RuleStatus.FAILED && violations.isEmpty()) {
            throw new IllegalArgumentException("A failed result requires at least one violation");
        }
        if ((status == RuleStatus.PASSED || status == RuleStatus.SKIPPED) && !violations.isEmpty()) {
            throw new IllegalArgumentException(status + " results cannot contain violations");
        }
        if ((status == RuleStatus.SKIPPED || status == RuleStatus.INCOMPLETE) && diagnostics.isEmpty()) {
            throw new IllegalArgumentException(status + " results require a diagnostic");
        }
    }
}
