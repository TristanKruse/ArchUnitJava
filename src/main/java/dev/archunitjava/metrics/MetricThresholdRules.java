package dev.archunitjava.metrics;

import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.rules.ArchitectureRule;
import dev.archunitjava.rules.ArchitectureRules;
import dev.archunitjava.rules.RuleTerminal;
import dev.archunitjava.selector.SelectorDescription;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Architecture rules that emit one structured violation for every subject outside a typed limit. */
public final class MetricThresholdRules {
    private MetricThresholdRules() {}

    public static ArchitectureRule enforce(
            Collection<MetricSample> samples, MetricThreshold threshold) {
        MetricThreshold limit = Objects.requireNonNull(threshold, "threshold");
        TreeMap<String, MetricSample> selected = new TreeMap<>();
        Objects.requireNonNull(samples, "samples").stream()
                .map(value -> Objects.requireNonNull(value, "sample"))
                .filter(value -> value.metric() == limit.metric())
                .forEach(value -> {
                    MetricSample previous = selected.putIfAbsent(
                            value.subject().stableKey(), value);
                    if (previous != null) {
                        throw new IllegalArgumentException(
                                "duplicate metric subject: " + value.subject().stableKey());
                    }
                });
        List<MetricSample> values = List.copyOf(selected.values());
        String identity = "metric-threshold:" + limit.metric() + ':' + limit.comparison()
                + ':' + limit.limit().stableValue() + ':' + limit.limit().unit();
        String description = limit.metric() + " must be " + limit.comparison() + ' '
                + limit.limit().stableValue() + ' ' + limit.limit().unit();
        return ArchitectureRules.define(identity, description, (metadata, options) ->
                RuleTerminal.evaluate(
                        metadata,
                        options,
                        new SelectorDescription("subjects measured for " + limit.metric()),
                        values.size(),
                        diagnostics -> evaluate(metadata, values, limit, diagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            List<MetricSample> samples,
            MetricThreshold threshold,
            List<Diagnostic> diagnostics) {
        List<Violation> violations = new ArrayList<>();
        for (MetricSample sample : samples) {
            if (!threshold.violatedBy(sample)) continue;
            violations.add(new Violation(
                    new ViolationId(metadata.semanticIdentity() + ':' + sample.subject().stableKey()),
                    "metric.threshold",
                    metadata.severity(),
                    List.of(new ViolationSubject("subject", sample.subject())),
                    List.of(),
                    Map.of(
                            "actual", sample.amount().stableValue(),
                            "comparison", threshold.comparison().name(),
                            "limit", threshold.limit().stableValue(),
                            "metric", threshold.metric().name(),
                            "unit", threshold.limit().unit().name())));
        }
        return violations.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, violations, diagnostics);
    }
}
