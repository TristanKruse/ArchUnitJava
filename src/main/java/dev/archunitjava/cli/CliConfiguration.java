package dev.archunitjava.cli;

import dev.archunitjava.execution.CheckOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Fully validated, immutable CLI configuration with resolved approved inputs. */
public record CliConfiguration(
        Path approvedRoot,
        Path source,
        List<Path> inputs,
        List<CliRuleConfiguration> rules,
        CheckOptions checkOptions,
        CliResultFormat resultFormat,
        CliGraphFormat graphFormat,
        CliGraphDomain graphDomain) {
    public static final String SCHEMA = "archunitjava.cli.v1";

    public CliConfiguration {
        approvedRoot = absolute(approvedRoot, "approvedRoot");
        source = absolute(source, "source");
        inputs = paths(inputs);
        if (inputs.isEmpty()) throw new CliConfigurationException("At least one input is required");
        Objects.requireNonNull(rules, "rules");
        TreeSet<CliRuleConfiguration> normalizedRules = new TreeSet<>(rules);
        if (normalizedRules.size() != rules.size()) {
            throw new CliConfigurationException("Rule ids must be unique");
        }
        if (normalizedRules.isEmpty()) {
            throw new CliConfigurationException("At least one rule is required");
        }
        rules = List.copyOf(normalizedRules);
        Objects.requireNonNull(checkOptions, "checkOptions");
        Objects.requireNonNull(resultFormat, "resultFormat");
        Objects.requireNonNull(graphFormat, "graphFormat");
        Objects.requireNonNull(graphDomain, "graphDomain");
    }

    public CliConfiguration withResultFormat(CliResultFormat value) {
        return new CliConfiguration(
                approvedRoot, source, inputs, rules, checkOptions,
                Objects.requireNonNull(value), graphFormat, graphDomain);
    }

    public CliConfiguration withGraphFormat(CliGraphFormat value) {
        return new CliConfiguration(
                approvedRoot, source, inputs, rules, checkOptions,
                resultFormat, Objects.requireNonNull(value), graphDomain);
    }

    private static Path absolute(Path value, String role) {
        Objects.requireNonNull(value, role);
        if (!value.isAbsolute()) throw new IllegalArgumentException(role + " must be absolute");
        return value.normalize();
    }

    private static List<Path> paths(List<Path> values) {
        Objects.requireNonNull(values, "inputs");
        TreeSet<Path> sorted = new TreeSet<>();
        for (Path value : values) sorted.add(absolute(value, "input"));
        return List.copyOf(sorted);
    }
}
