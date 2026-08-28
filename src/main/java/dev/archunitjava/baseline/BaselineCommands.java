package dev.archunitjava.baseline;

import dev.archunitjava.report.ResultReport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Explicit, side-effect-free commands for freezing and proposing baseline updates. */
public final class BaselineCommands {
    private BaselineCommands() {}

    public static ReviewedBaseline freeze(ResultReport results) {
        return ReviewedBaseline.of(BaselineComparison.capture(
                Objects.requireNonNull(results, "results")), List.of());
    }

    public static ReviewedBaseline freeze(
            ResultReport results, Collection<Suppression> suppressions, LocalDate onDate) {
        ResultReport report = Objects.requireNonNull(results, "results");
        ReviewedBaseline withSuppressions = ReviewedBaseline.of(List.of(), suppressions);
        BaselineComparison comparison = BaselineComparison.compare(
                withSuppressions, report, Objects.requireNonNull(onDate, "onDate"));
        List<BaselineFinding> reviewed = comparison.changes().stream()
                .filter(change -> change.state() != FindingState.SUPPRESSED)
                .map(change -> change.current().orElseThrow())
                .toList();
        return ReviewedBaseline.of(reviewed, suppressions);
    }

    public static BaselineUpdate update(
            ReviewedBaseline baseline, ResultReport results, LocalDate onDate) {
        ReviewedBaseline reviewed = Objects.requireNonNull(baseline, "baseline");
        BaselineComparison comparison = BaselineComparison.compare(
                reviewed, Objects.requireNonNull(results, "results"),
                Objects.requireNonNull(onDate, "onDate"));
        List<BaselineFinding> proposed = new ArrayList<>();
        for (FindingChange change : comparison.changes()) {
            if (change.current().isPresent() && change.state() != FindingState.SUPPRESSED) {
                proposed.add(change.current().orElseThrow());
            }
        }
        return new BaselineUpdate(
                comparison, ReviewedBaseline.of(proposed, reviewed.suppressions()));
    }
}
