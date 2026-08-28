package dev.archunitjava.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.report.ResultReport;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BaselineAdoptionTest {
    private static final LocalDate REVIEW_DATE = LocalDate.of(2026, 8, 28);

    @Test
    void fingerprintsSeparateLogicalIdentityFromMovedEvidence() {
        BaselineFinding original = finding("rule", "same", "classes/Old.class");
        BaselineFinding moved = finding("rule", "same", "classes/New.class");
        BaselineFinding different = finding("rule", "new", "classes/Old.class");

        assertEquals(original.identityFingerprint(), moved.identityFingerprint());
        assertNotEquals(original.evidenceFingerprint(), moved.evidenceFingerprint());
        assertNotEquals(original.exactFingerprint(), moved.exactFingerprint());
        assertNotEquals(original.identityFingerprint(), different.identityFingerprint());
    }

    @Test
    void comparisonDistinguishesNewUnchangedMovedAndResolvedFindings() {
        ResultReport previous = report(
                violation("same", "classes/Same.class"),
                violation("move", "classes/Old.class"),
                violation("gone", "classes/Gone.class"));
        ReviewedBaseline baseline = BaselineCommands.freeze(previous);
        ResultReport current = report(
                violation("same", "classes/Same.class"),
                violation("move", "classes/New.class"),
                violation("new", "classes/NewFinding.class"));

        BaselineComparison comparison = BaselineComparison.compare(baseline, current, REVIEW_DATE);

        assertEquals(1, comparison.count(FindingState.NEW));
        assertEquals(1, comparison.count(FindingState.UNCHANGED));
        assertEquals(1, comparison.count(FindingState.MOVED));
        assertEquals(1, comparison.count(FindingState.RESOLVED));
    }

    @Test
    void scopedSuppressionsRequireRationaleAndExposeExpiry() {
        String subject = TypeId.ofBinaryName("com.example.Bad").stableKey();
        Suppression active = new Suppression(
                "temporary", "Migration tracked in ENG-42", Optional.of("rule"),
                Optional.of(subject), Optional.of("classes/Same.class"),
                Optional.of(REVIEW_DATE));
        Suppression expired = new Suppression(
                "expired", "Legacy exception", Optional.of("rule"), Optional.empty(),
                Optional.of("classes/Old.class"), Optional.of(REVIEW_DATE.minusDays(1)));
        ReviewedBaseline baseline = ReviewedBaseline.of(List.of(), List.of(expired, active));

        BaselineComparison comparison = BaselineComparison.compare(
                baseline,
                report(violation("same", "classes/Same.class"),
                        violation("move", "classes/Old.class")),
                REVIEW_DATE);

        assertEquals(1, comparison.count(FindingState.SUPPRESSED));
        assertEquals(1, comparison.count(FindingState.EXPIRED));
        assertFalse(active.expiredOn(REVIEW_DATE));
        assertTrue(active.expiredOn(REVIEW_DATE.plusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> Suppression.forRule("id", " ", "rule", Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new Suppression("id", "reason", Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(REVIEW_DATE)));
    }

    @Test
    void explicitUpdatesProduceStableReviewableDiffsAndCanonicalJson() {
        ReviewedBaseline baseline = BaselineCommands.freeze(report(
                violation("move", "classes/Old.class"),
                violation("gone", "classes/Gone.class")));
        ResultReport current = report(
                violation("new", "classes/New.class"),
                violation("move", "classes/Moved.class"));

        BaselineUpdate first = BaselineCommands.update(baseline, current, REVIEW_DATE);
        BaselineUpdate second = BaselineCommands.update(
                baseline, ResultReport.of(current.results().reversed()), REVIEW_DATE);

        assertEquals(first, second);
        assertEquals(first.deterministicDiff(), second.deterministicDiff());
        assertTrue(first.deterministicDiff().contains("+ NEW"));
        assertTrue(first.deterministicDiff().contains("~ MOVED"));
        assertTrue(first.deterministicDiff().contains("- RESOLVED"));
        String json = BaselineJsonRenderer.render(first.proposedBaseline());
        assertTrue(json.startsWith("{\"schemaVersion\":\"archunitjava.baseline.v1\""));
        assertEquals(json, BaselineJsonRenderer.render(first.proposedBaseline()));
    }

    @Test
    void activeSuppressionsAreNotSilentlyFrozenIntoTheProposedBaseline() {
        Suppression suppression = Suppression.forRule(
                "migration", "Known debt", "rule", Optional.of(REVIEW_DATE.plusDays(7)));
        ReviewedBaseline baseline = ReviewedBaseline.of(List.of(), List.of(suppression));

        BaselineUpdate update = BaselineCommands.update(
                baseline, report(violation("same", "classes/Same.class")), REVIEW_DATE);

        assertTrue(update.proposedBaseline().findings().isEmpty());
        assertEquals(List.of(suppression), update.proposedBaseline().suppressions());
        assertTrue(update.deterministicDiff().contains("s SUPPRESSED"));
    }

    private static BaselineFinding finding(String rule, String id, String location) {
        RuleResult result = RuleResult.failed(rule, List.of(violation(id, location)), List.of());
        return BaselineFinding.capture(result, result.violations().getFirst());
    }

    private static ResultReport report(Violation... violations) {
        return ResultReport.of(List.of(
                RuleResult.failed("rule", List.of(violations), List.of())));
    }

    private static Violation violation(String id, String location) {
        return new Violation(
                new ViolationId(id), "dependency.forbidden", Severity.ERROR,
                List.of(new ViolationSubject(
                        "origin", TypeId.ofBinaryName("com.example.Bad"))),
                List.of(DependencyEvidence.at(LocationId.ofResourcePath(location))),
                Map.of("policy", "boundary"));
    }
}
