package dev.archunitjava.junit;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.report.ResultRenderLimits;
import dev.archunitjava.rules.ArchitectureRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Creates isolated executable cases for ordinary or dynamic architecture tests. */
public final class ArchitectureTestCases {
    private ArchitectureTestCases() {}

    public static List<ArchitectureTestCase> forRules(
            Collection<? extends ArchitectureRule> rules) {
        return forRules(rules, CheckOptions.defaults(), ResultRenderLimits.defaults());
    }

    public static List<ArchitectureTestCase> forRules(
            Collection<? extends ArchitectureRule> rules,
            CheckOptions options,
            ResultRenderLimits limits) {
        Objects.requireNonNull(rules, "rules");
        CheckOptions checkOptions = Objects.requireNonNull(options, "options");
        ResultRenderLimits renderLimits = Objects.requireNonNull(limits, "limits");
        TreeMap<String, ArchitectureRule> byIdentity = new TreeMap<>();
        for (ArchitectureRule rule : rules) {
            Objects.requireNonNull(rule, "rule");
            if (byIdentity.putIfAbsent(rule.metadata().semanticIdentity(), rule) != null) {
                throw new IllegalArgumentException(
                        "Duplicate architecture rule: " + rule.metadata().semanticIdentity());
            }
        }
        List<ArchitectureTestCase> cases = new ArrayList<>();
        byIdentity.forEach((identity, rule) -> cases.add(new ArchitectureTestCase(
                identity,
                rule.metadata().displayName(),
                () -> ArchitectureAssertions.assertPasses(rule, checkOptions, renderLimits))));
        return List.copyOf(cases);
    }
}
