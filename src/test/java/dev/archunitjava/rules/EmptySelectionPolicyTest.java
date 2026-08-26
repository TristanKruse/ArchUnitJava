package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.execution.EmptySelectionPolicy;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.result.Severity;
import dev.archunitjava.selector.SelectorDescription;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EmptySelectionPolicyTest {
    private static final SelectorDescription SELECTOR =
            new SelectorDescription("binary name matches com.example..Missing");

    @Test
    void strictDefaultFailsWithActionableSelectorDiagnostics() {
        ArchitectureRule rule = emptyRule(new AtomicInteger());

        RuleResult result = rule.check();

        assertEquals(EmptySelectionPolicy.FAIL, CheckOptions.defaults().emptySelectionPolicy());
        assertEquals(RuleStatus.INCOMPLETE, result.status());
        assertEquals(RuleTerminal.EMPTY_SELECTION_CODE, result.diagnostics().getFirst().code());
        assertEquals(Severity.ERROR, result.diagnostics().getFirst().severity());
        assertEquals(SELECTOR.text(), result.diagnostics().getFirst().context().get("selector"));
        assertTrue(result.diagnostics().getFirst().context().get("remediation").contains("CheckOptions"));
    }

    @Test
    void warnRunsTheRuleAndAllowRunsSilently() {
        AtomicInteger evaluations = new AtomicInteger();
        ArchitectureRule rule = emptyRule(evaluations);
        CheckOptions warn = CheckOptions.builder()
                .emptySelectionPolicy(EmptySelectionPolicy.WARN).build();
        CheckOptions allow = CheckOptions.builder()
                .emptySelectionPolicy(EmptySelectionPolicy.ALLOW).build();

        RuleResult warning = rule.check(warn);
        RuleResult allowed = rule.check(allow);

        assertEquals(RuleStatus.PASSED, warning.status());
        assertEquals(Severity.WARNING, warning.diagnostics().getFirst().severity());
        assertEquals(RuleStatus.PASSED, allowed.status());
        assertTrue(allowed.diagnostics().isEmpty());
        assertEquals(2, evaluations.get());
    }

    @Test
    void nonEmptySelectionsIgnoreTheEmptyPolicyAndBooleanCompatibilityRemains() {
        CheckOptions legacyAllow = CheckOptions.builder().allowEmptySelection(true).build();
        CheckOptions legacyFail = CheckOptions.builder().allowEmptySelection(false).build();
        AtomicInteger evaluations = new AtomicInteger();
        ArchitectureRule rule = ArchitectureRules.define(
                "non-empty", "non-empty selection", (metadata, options) ->
                        RuleTerminal.evaluate(
                                metadata, options, SELECTOR, 1,
                                diagnostics -> {
                                    evaluations.incrementAndGet();
                                    return RuleResult.passed(metadata, diagnostics);
                                }));

        assertEquals(EmptySelectionPolicy.ALLOW, legacyAllow.emptySelectionPolicy());
        assertEquals(EmptySelectionPolicy.FAIL, legacyFail.emptySelectionPolicy());
        assertTrue(legacyAllow.allowEmptySelection());
        assertEquals(RuleStatus.PASSED, rule.check(legacyFail).status());
        assertEquals(1, evaluations.get());
    }

    private static ArchitectureRule emptyRule(AtomicInteger evaluations) {
        return ArchitectureRules.define(
                "empty-fixture", "empty fixture", (metadata, options) ->
                        RuleTerminal.evaluate(
                                metadata, options, SELECTOR, 0,
                                diagnostics -> {
                                    evaluations.incrementAndGet();
                                    return RuleResult.passed(metadata, diagnostics);
                                }));
    }
}
