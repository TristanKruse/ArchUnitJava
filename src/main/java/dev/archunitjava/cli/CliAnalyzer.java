package dev.archunitjava.cli;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassPathImportResolver;
import dev.archunitjava.report.ResultReport;
import java.util.Objects;

/** Direct Java API for the same bounded import and rule evaluation performed by the CLI. */
public final class CliAnalyzer {
    public CliAnalysisResult analyze(CliConfiguration configuration) {
        CliConfiguration config = Objects.requireNonNull(configuration, "configuration");
        var imports = new ClassPathImportResolver().resolve(config.inputs().stream()
                .map(ClassFileInput::path).toList());
        var graph = CliGraphBuilder.build(imports.model());
        var results = CliRuleFactory.createRules(config, imports.model(), graph).stream()
                .map(rule -> rule.check(config.checkOptions()))
                .toList();
        return new CliAnalysisResult(imports, graph, ResultReport.of(results));
    }
}
