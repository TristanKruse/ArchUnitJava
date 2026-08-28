package dev.archunitjava.junit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.execution.TechnicalError;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.report.ResultRenderLimits;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.rules.ArchitectureRule;
import dev.archunitjava.rules.ArchitectureRules;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class ArchitectureAssertionsTest {
    @Test
    void passingRuleIsSilentAndReceivesExplicitOptions() {
        AtomicReference<CheckOptions> observed = new AtomicReference<>();
        CheckOptions options = CheckOptions.builder().allowEmptySelection(true).build();

        assertDoesNotThrow(() -> ArchitectureAssertions.assertPasses(checkOptions -> {
            observed.set(checkOptions);
            return RuleResult.passed("pass");
        }, options));

        assertSame(options, observed.get());
    }

    @Test
    void policyFailureThrowsOneAssertionContainingAllBoundedViolations() {
        RuleResult result = RuleResult.failed(
                "policy", List.of(
                        violation("first", "classes/First.class"),
                        violation("second", "classes/Second.class"),
                        violation("third", "classes/Third.class")), List.of());

        ArchitecturePolicyAssertionError error = assertThrows(
                ArchitecturePolicyAssertionError.class,
                () -> ArchitectureAssertions.assertPasses(
                        options -> result, new ResultRenderLimits(2, 1, 1)));

        assertSame(result, error.result());
        assertTrue(error.getMessage().contains("first"));
        assertTrue(error.getMessage().contains("second"));
        assertFalse(error.getMessage().contains("third [ERROR]"));
        assertTrue(error.getMessage().contains("... 1 more violations"));
    }

    @Test
    void incompleteSkippedAndThrownAnalysisRemainDistinctFromPolicyFailures() {
        Diagnostic diagnostic = new Diagnostic(
                "analysis.missing", Severity.ERROR, Map.of("type", "example.Missing"));
        RuleResult incomplete = RuleResult.incomplete("rule", List.of(), List.of(diagnostic));

        ArchitectureAnalysisAssertionError incompleteError = assertThrows(
                ArchitectureAnalysisAssertionError.class,
                () -> ArchitectureAssertions.assertPasses(options -> incomplete));
        assertEquals("analysis.incomplete", incompleteError.executionCode().orElseThrow());
        assertSame(incomplete, incompleteError.result().orElseThrow());

        RuleResult skipped = RuleResult.skipped("rule", List.of(diagnostic));
        ArchitectureAnalysisAssertionError skippedError = assertThrows(
                ArchitectureAnalysisAssertionError.class,
                () -> ArchitectureAssertions.assertPasses(options -> skipped));
        assertEquals("analysis.skipped", skippedError.executionCode().orElseThrow());

        TechnicalError technical = new TechnicalError(
                "classpath.io", "Could not read classpath", null, Map.of("entry", "bad.jar"));
        ArchitectureAnalysisAssertionError technicalError = assertThrows(
                ArchitectureAnalysisAssertionError.class,
                () -> ArchitectureAssertions.assertPasses(options -> { throw technical; }));
        assertEquals("classpath.io", technicalError.executionCode().orElseThrow());
        assertEquals(Map.of("entry", "bad.jar"), technicalError.context());
        assertSame(technical, technicalError.getCause());
    }

    @Test
    void testCasesAreImmutableOrderedAndRejectDuplicateSemanticRules() {
        ArchitectureRule second = passingRule("second", "Second rule");
        ArchitectureRule first = passingRule("first", "First rule");
        List<ArchitectureTestCase> cases = ArchitectureTestCases.forRules(List.of(second, first));

        assertEquals(List.of("first", "second"),
                cases.stream().map(ArchitectureTestCase::semanticIdentity).toList());
        cases.forEach(ArchitectureTestCase::execute);
        assertThrows(UnsupportedOperationException.class, () -> cases.clear());
        assertThrows(IllegalArgumentException.class,
                () -> ArchitectureTestCases.forRules(List.of(first, first.as("alias"))));
    }

    @Test
    void ordinaryJupiterTestUsageNeedsNoAdapter() {
        ArchitectureAssertions.assertPasses(passingRule("ordinary", "Ordinary test"));
    }

    @TestFactory
    Stream<DynamicTest> dynamicJupiterTestsUseIsolatedExecutableCases() {
        return ArchitectureTestCases.forRules(List.of(
                        passingRule("dynamic.two", "Dynamic two"),
                        passingRule("dynamic.one", "Dynamic one")))
                .stream()
                .map(test -> dynamicTest(test.displayName(), test::execute));
    }

    private static ArchitectureRule passingRule(String id, String description) {
        return ArchitectureRules.define(
                id, description, (metadata, options) -> RuleResult.passed(metadata));
    }

    private static Violation violation(String id, String location) {
        return new Violation(
                new ViolationId(id), "dependency.forbidden", Severity.ERROR,
                List.of(new ViolationSubject(
                        "origin", TypeId.ofBinaryName("com.example." + Character.toUpperCase(
                                id.charAt(0)) + id.substring(1)))),
                List.of(DependencyEvidence.at(LocationId.ofResourcePath(location))),
                Map.of());
    }
}
