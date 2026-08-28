package dev.archunitjava.report;

import dev.archunitjava.result.RuleResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, deterministically ordered results from one architecture evaluation. */
public record ResultReport(String schemaVersion, List<RuleResult> results) {
    public static final String CURRENT_SCHEMA_VERSION = "archunitjava.rule-results.v1";

    public ResultReport {
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported result schema: " + schemaVersion);
        }
        Objects.requireNonNull(results, "results");
        TreeMap<String, RuleResult> byRule = new TreeMap<>();
        for (RuleResult result : results) {
            Objects.requireNonNull(result, "result");
            RuleResult previous = byRule.putIfAbsent(result.ruleId(), result);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Result report contains rule more than once: " + result.ruleId());
            }
        }
        results = List.copyOf(new ArrayList<>(byRule.values()));
    }

    public static ResultReport of(Collection<RuleResult> results) {
        Objects.requireNonNull(results, "results");
        return new ResultReport(CURRENT_SCHEMA_VERSION, List.copyOf(results));
    }
}
