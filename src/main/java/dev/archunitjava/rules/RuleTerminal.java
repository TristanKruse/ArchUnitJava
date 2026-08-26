package dev.archunitjava.rules;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.execution.EmptySelectionPolicy;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.selector.SelectorDescription;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The single empty-selection gate shared by every architecture-rule terminal. */
public final class RuleTerminal {
    public static final String EMPTY_SELECTION_CODE = "rule.selection.empty";

    private RuleTerminal() {}

    public static RuleResult evaluate(
            RuleMetadata metadata,
            CheckOptions options,
            SelectorDescription selector,
            int selectedCount,
            RuleResultFactory ordinaryEvaluation) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(selector, "selector");
        if (selectedCount < 0) {
            throw new IllegalArgumentException("selectedCount must not be negative");
        }
        Objects.requireNonNull(ordinaryEvaluation, "ordinaryEvaluation");
        if (selectedCount > 0 || options.emptySelectionPolicy() == EmptySelectionPolicy.ALLOW) {
            return requireMetadata(metadata, ordinaryEvaluation.create(List.of()));
        }
        Diagnostic diagnostic = diagnostic(selector, options.emptySelectionPolicy());
        if (options.emptySelectionPolicy() == EmptySelectionPolicy.WARN) {
            return requireMetadata(metadata, ordinaryEvaluation.create(List.of(diagnostic)));
        }
        return RuleResult.incomplete(metadata, List.of(), List.of(diagnostic));
    }

    private static Diagnostic diagnostic(
            SelectorDescription selector, EmptySelectionPolicy policy) {
        Severity severity = policy == EmptySelectionPolicy.FAIL ? Severity.ERROR : Severity.WARNING;
        return new Diagnostic(
                EMPTY_SELECTION_CODE,
                severity,
                Map.of(
                        "policy", policy.name(),
                        "remediation",
                                "Correct the selector or deliberately choose ALLOW/WARN in CheckOptions",
                        "selector", selector.text()));
    }

    private static RuleResult requireMetadata(RuleMetadata metadata, RuleResult result) {
        RuleResult value = Objects.requireNonNull(result, "rule result");
        if (!value.metadata().equals(metadata)) {
            throw new IllegalStateException("Rule terminal result replaced its supplied metadata");
        }
        return value;
    }
}
