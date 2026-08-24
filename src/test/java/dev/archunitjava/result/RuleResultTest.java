package dev.archunitjava.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.execution.Checkable;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.TypeId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class RuleResultTest {
    @Test
    void violationsNormalizeStructuredSubjectsEvidenceAndAttributes() {
        TypeId origin = TypeId.ofBinaryName("com.example.Origin");
        PackageId target = PackageId.named("com.example.internal");
        DependencyEvidence later = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/Z.class"));
        DependencyEvidence earlier = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/A.class"));
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("targetPackage", target.qualifiedName());
        attributes.put("policy", "api-boundary");

        Violation violation = new Violation(
                new ViolationId("layers:no-internal-access:com.example.Origin"),
                "dependency.internal-package",
                Severity.ERROR,
                List.of(
                        new ViolationSubject("target", target),
                        new ViolationSubject("origin", origin),
                        new ViolationSubject("origin", origin)),
                List.of(later, earlier, earlier),
                attributes);
        attributes.put("later", "must-not-leak");

        assertEquals(List.of(
                new ViolationSubject("origin", origin),
                new ViolationSubject("target", target)), violation.subjects());
        assertEquals(List.of(earlier, later), violation.evidence());
        assertEquals(List.of("policy", "targetPackage"), List.copyOf(violation.attributes().keySet()));
        assertFalse(violation.attributes().containsKey("later"));
        assertThrows(UnsupportedOperationException.class, () -> violation.subjects().clear());
        assertThrows(UnsupportedOperationException.class, () -> violation.attributes().clear());
    }

    @Test
    void equivalentResultsCompareAndIterateDeterministically() {
        Violation first = violation("rule:first", "com.example.A");
        Violation second = violation("rule:second", "com.example.B");
        Diagnostic alpha = diagnostic("analysis.alpha", "A");
        Diagnostic omega = diagnostic("analysis.omega", "Z");
        RuleResult left = RuleResult.failed(
                "dependency-rule", List.of(second, first, first), List.of(omega, alpha, alpha));
        RuleResult right = RuleResult.failed(
                "dependency-rule", List.of(first, second), List.of(alpha, omega));

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertEquals(0, left.compareTo(right));
        assertEquals(List.of(first, second), left.violations());
        assertEquals(List.of(alpha, omega), left.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> left.violations().clear());
    }

    @Test
    void allTerminalStatesRemainExplicitAndNonOverlapping() {
        Violation violation = violation("rule:failure", "com.example.Bad");
        Diagnostic reason = diagnostic("selection.empty", "com.example..*");
        RuleResult passed = RuleResult.passed("rule");
        RuleResult failed = RuleResult.failed("rule", List.of(violation), List.of());
        RuleResult skipped = RuleResult.skipped("rule", List.of(reason));
        RuleResult incomplete = RuleResult.incomplete("rule", List.of(violation), List.of(reason));

        assertTrue(passed.passed());
        assertEquals(RuleStatus.PASSED, passed.status());
        assertEquals(RuleStatus.FAILED, failed.status());
        assertEquals(RuleStatus.SKIPPED, skipped.status());
        assertEquals(RuleStatus.INCOMPLETE, incomplete.status());
        assertFalse(incomplete.passed());
        assertEquals(4, new TreeSet<>(List.of(passed, failed, skipped, incomplete)).size());
    }

    @Test
    void invalidOrAmbiguousResultStatesFailDuringConstruction() {
        Violation violation = violation("same-id", "com.example.A");
        Violation conflicting = new Violation(
                violation.id(), "different.code", Severity.ERROR,
                violation.subjects(), violation.evidence(), violation.attributes());
        Diagnostic reason = diagnostic("analysis.incomplete", "missing.class");

        assertThrows(IllegalArgumentException.class,
                () -> RuleResult.failed("rule", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RuleResult.skipped("rule", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RuleResult.incomplete("rule", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RuleResult.failed("rule", List.of(violation, conflicting), List.of(reason)));
        assertThrows(IllegalArgumentException.class,
                () -> new Violation(
                        new ViolationId("id"), "code", Severity.ERROR,
                        List.of(), List.of(), Map.of()));
    }

    @Test
    void ruleFailuresFlowThroughTheSharedTerminalAsValues() {
        Violation violation = violation("rule:failure", "com.example.Bad");
        Checkable<RuleResult> checkable = options ->
                RuleResult.failed("dependency-rule", List.of(violation), List.of());

        RuleResult result = checkable.check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(List.of(violation), result.violations());
    }

    @Test
    void resultValuesRejectBlankMachineIdentifiersAndContext() {
        assertThrows(IllegalArgumentException.class, () -> new ViolationId(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new ViolationSubject("", TypeId.ofBinaryName("com.example.Type")));
        assertThrows(IllegalArgumentException.class,
                () -> new Diagnostic("code", Severity.WARNING, Map.of("path", " ")));
        assertThrows(IllegalArgumentException.class, () -> RuleResult.passed(" "));
    }

    private static Violation violation(String id, String binaryName) {
        TypeId type = TypeId.ofBinaryName(binaryName);
        return new Violation(
                new ViolationId(id),
                "dependency.forbidden",
                Severity.ERROR,
                List.of(new ViolationSubject("subject", type)),
                List.of(DependencyEvidence.at(LocationId.ofResourcePath(
                        "classes/" + binaryName.replace('.', '/') + ".class"))),
                Map.of("policy", "forbidden-dependency"));
    }

    private static Diagnostic diagnostic(String code, String value) {
        return new Diagnostic(code, Severity.WARNING, Map.of("detail", value));
    }
}
