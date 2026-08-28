package dev.archunitjava.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResultRenderersTest {
    @Test
    void jsonIsVersionedLosslessDeterministicAndUtf8() {
        ResultReport report = report();
        String first = ResultJsonRenderer.render(report);
        String second = ResultJsonRenderer.render(ResultReport.of(report.results().reversed()));

        assertEquals(first, second);
        assertTrue(first.contains("\"schemaVersion\":\"archunitjava.rule-results.v1\""));
        assertTrue(first.contains("\"semanticIdentity\":\"dependency-rule\""));
        assertTrue(first.contains("\"presentationIdentity\":"));
        assertTrue(first.contains("\"rationale\":\"Keep \\u0026lt;safe\\u0026gt; \\u0026 reliable\""));
        assertTrue(first.contains("\"location\":\"classes/com/example/A \\u0026 B.class\""));
        assertTrue(first.contains("\"status\":\"INCOMPLETE\""));
        assertArrayEquals(first.getBytes(StandardCharsets.UTF_8),
                ResultJsonRenderer.renderBytes(report));
    }

    @Test
    void sarifUsesRepositoryRelativeEncodedLocationsAndAnalysisNotifications() {
        String sarif = SarifResultRenderer.render(report());

        assertTrue(sarif.contains("\"version\":\"2.1.0\""));
        assertTrue(sarif.contains("\"uri\":\"classes/com/example/A%20%26%20B.class\""));
        assertTrue(sarif.contains("\"uriBaseId\":\"%SRCROOT%\""));
        assertTrue(sarif.contains("\"violationId\":\"violation-\\u003c1\\u003e\""));
        assertTrue(sarif.contains("\"executionSuccessful\":false"));
        assertTrue(sarif.contains("\"kind\":\"analysis-error\""));
        assertFalse(sarif.contains("C:\\\\"));
        assertFalse(sarif.contains("../"));
    }

    @Test
    void junitXmlDistinguishesPolicyFailuresAnalysisErrorsAndSkippedRules() {
        String xml = JunitXmlResultRenderer.render(report(), new ResultRenderLimits(1, 0, 1));

        assertTrue(xml.contains("tests=\"4\" failures=\"1\" errors=\"1\" skipped=\"1\""));
        assertTrue(xml.contains("<failure type=\"policy-violation\""));
        assertTrue(xml.contains("<error type=\"analysis-error\""));
        assertTrue(xml.contains("<skipped message=\"Architecture rule skipped\"/>"));
        assertTrue(xml.contains("name=\"Dependency &amp; &lt;rule&gt;\""));
        assertFalse(xml.contains("\u0001"));
        assertTrue(xml.contains("... 2 more evidence"));
    }

    @Test
    void consoleOutputIsBoundedSingleLineSafeAndLabelsFailureKinds() {
        String console = ConsoleResultRenderer.render(
                report(), new ResultRenderLimits(1, 0, 1));

        assertTrue(console.contains("[PASS] pass"));
        assertTrue(console.contains("[FAIL:POLICY] Dependency & <rule>"));
        assertTrue(console.contains("[ERROR:ANALYSIS] incomplete"));
        assertTrue(console.contains("[SKIP] skipped"));
        assertTrue(console.contains("... 2 more evidence"));
        assertTrue(console.contains("hostile\\ncontext"));
        assertFalse(console.contains("hostile\ncontext"));
    }

    @Test
    void reportRejectsDuplicateRuleEvaluationsAndInvalidLimits() {
        RuleResult result = RuleResult.passed("same");
        assertThrows(IllegalArgumentException.class,
                () -> ResultReport.of(List.of(result, result)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResultRenderLimits(-1, 0, 0));
    }

    private static ResultReport report() {
        RuleMetadata metadata = RuleMetadata.of("dependency-rule", "Dependency <rule>")
                .as("Dependency & <rule>")
                .because("Keep &lt;safe&gt; & reliable")
                .tagged("dependencies", "security")
                .withSeverity(Severity.WARNING);
        TypeId type = TypeId.ofBinaryName("com.example.Bad");
        DependencyEvidence evidence = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/A & B.class"));
        Violation violation = new Violation(
                new ViolationId("violation-<1>"), "dependency.<forbidden>", Severity.ERROR,
                List.of(new ViolationSubject("origin", type)), List.of(evidence, DependencyEvidence.at(
                        LocationId.ofResourcePath("classes/com/example/Z.class"))),
                Map.of("detail", "<script>&\""));
        Diagnostic incomplete = new Diagnostic(
                "analysis.failed", Severity.ERROR, Map.of("detail", "hostile\ncontext\u0001"));
        Diagnostic skipped = new Diagnostic(
                "selection.empty", Severity.WARNING, Map.of("selector", "none"));
        return ResultReport.of(List.of(
                RuleResult.skipped("skipped", List.of(skipped)),
                RuleResult.incomplete("incomplete", List.of(), List.of(incomplete)),
                RuleResult.failed(metadata, List.of(violation), List.of()),
                RuleResult.passed("pass")));
    }
}
