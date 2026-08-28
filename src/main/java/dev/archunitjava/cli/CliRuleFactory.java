package dev.archunitjava.cli;

import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.rules.ArchitectureRule;
import dev.archunitjava.rules.DependencyRuleSpec;
import dev.archunitjava.rules.DependencyRules;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Public Java API counterpart to the CLI's fixed dependency-rule mapping. */
public final class CliRuleFactory {
    private CliRuleFactory() {}

    public static List<ArchitectureRule> createRules(
            CliConfiguration configuration, TypeModelResult model, DependencyGraph graph) {
        CliConfiguration config = Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        List<ArchitectureRule> rules = new ArrayList<>();
        for (CliRuleConfiguration definition : config.rules()) {
            DependencyRuleSpec spec = new DependencyRuleSpec(
                    definition.mode(), definition.selfDependencies(),
                    definition.externalDependencies());
            ArchitectureRule rule = switch (definition.domain()) {
                case TYPES -> DependencyRules.types(
                        model,
                        graph,
                        TypeSelector.binaryName(definition.origins().toJavaPattern()),
                        TypeSelector.binaryName(definition.targets().toJavaPattern()),
                        spec);
                case PACKAGES -> DependencyRules.packages(
                        model,
                        graph,
                        PackageSelector.name(definition.origins().toJavaPattern()),
                        PackageSelector.name(definition.targets().toJavaPattern()),
                        spec);
            };
            if (definition.displayName().isPresent()) {
                rule = rule.as(definition.displayName().orElseThrow());
            }
            if (definition.rationale().isPresent()) {
                rule = rule.because(definition.rationale().orElseThrow());
            }
            if (!definition.tags().isEmpty()) rule = rule.tagged(definition.tags());
            rule = rule.withSeverity(definition.severity());
            rules.add(rule);
        }
        return List.copyOf(rules);
    }
}
