package dev.archunitjava.baseline;

import dev.archunitjava.report.ResultReport;
import dev.archunitjava.result.RuleResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic classification of current findings against a reviewed baseline. */
public record BaselineComparison(LocalDate onDate, List<FindingChange> changes) {
    public BaselineComparison {
        Objects.requireNonNull(onDate, "onDate");
        Objects.requireNonNull(changes, "changes");
        ArrayList<FindingChange> sorted = new ArrayList<>(changes);
        sorted.forEach(change -> Objects.requireNonNull(change, "change"));
        sorted.sort(null);
        changes = List.copyOf(sorted);
    }

    public long count(FindingState state) {
        Objects.requireNonNull(state, "state");
        return changes.stream().filter(change -> change.state() == state).count();
    }

    public static BaselineComparison compare(
            ReviewedBaseline baseline, ResultReport current, LocalDate onDate) {
        ReviewedBaseline reviewed = Objects.requireNonNull(baseline, "baseline");
        ResultReport report = Objects.requireNonNull(current, "current");
        Objects.requireNonNull(onDate, "onDate");
        List<BaselineFinding> currentFindings = capture(report);
        Set<Integer> consumed = new HashSet<>();
        List<FindingChange> changes = new ArrayList<>();

        for (BaselineFinding finding : currentFindings) {
            int match = exactMatch(reviewed.findings(), consumed, finding);
            if (match < 0) match = identityMatch(reviewed.findings(), consumed, finding);
            Optional<BaselineFinding> previous = match < 0
                    ? Optional.empty() : Optional.of(reviewed.findings().get(match));
            if (match >= 0) consumed.add(match);

            Optional<Suppression> active = reviewed.suppressions().stream()
                    .filter(suppression -> suppression.matches(finding))
                    .filter(suppression -> !suppression.expiredOn(onDate))
                    .findFirst();
            if (active.isPresent()) {
                changes.add(new FindingChange(FindingState.SUPPRESSED, previous,
                        Optional.of(finding), Optional.of(active.orElseThrow().id())));
                continue;
            }
            Optional<Suppression> expired = reviewed.suppressions().stream()
                    .filter(suppression -> suppression.matches(finding))
                    .filter(suppression -> suppression.expiredOn(onDate))
                    .findFirst();
            if (expired.isPresent()) {
                changes.add(new FindingChange(FindingState.EXPIRED, previous,
                        Optional.of(finding), Optional.of(expired.orElseThrow().id())));
            } else if (previous.isEmpty()) {
                changes.add(new FindingChange(FindingState.NEW, Optional.empty(),
                        Optional.of(finding), Optional.empty()));
            } else if (previous.orElseThrow().exactFingerprint().equals(finding.exactFingerprint())) {
                changes.add(new FindingChange(FindingState.UNCHANGED, previous,
                        Optional.of(finding), Optional.empty()));
            } else {
                changes.add(new FindingChange(FindingState.MOVED, previous,
                        Optional.of(finding), Optional.empty()));
            }
        }
        for (int index = 0; index < reviewed.findings().size(); index++) {
            if (!consumed.contains(index)) {
                changes.add(new FindingChange(FindingState.RESOLVED,
                        Optional.of(reviewed.findings().get(index)), Optional.empty(), Optional.empty()));
            }
        }
        return new BaselineComparison(onDate, changes);
    }

    static List<BaselineFinding> capture(ResultReport report) {
        List<BaselineFinding> findings = new ArrayList<>();
        for (RuleResult result : report.results()) {
            result.violations().forEach(violation -> findings.add(
                    BaselineFinding.capture(result, violation)));
        }
        findings.sort(null);
        return List.copyOf(findings);
    }

    private static int exactMatch(
            List<BaselineFinding> values, Set<Integer> consumed, BaselineFinding current) {
        for (int index = 0; index < values.size(); index++) {
            if (!consumed.contains(index) && values.get(index).exactFingerprint()
                    .equals(current.exactFingerprint())) return index;
        }
        return -1;
    }

    private static int identityMatch(
            List<BaselineFinding> values, Set<Integer> consumed, BaselineFinding current) {
        for (int index = 0; index < values.size(); index++) {
            if (!consumed.contains(index) && values.get(index).identityFingerprint()
                    .equals(current.identityFingerprint())) return index;
        }
        return -1;
    }
}
