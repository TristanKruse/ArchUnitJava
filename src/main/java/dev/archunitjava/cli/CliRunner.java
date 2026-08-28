package dev.archunitjava.cli;

import dev.archunitjava.report.ConsoleResultRenderer;
import dev.archunitjava.report.CsvGraphRenderer;
import dev.archunitjava.report.D2GraphRenderer;
import dev.archunitjava.report.DotGraphRenderer;
import dev.archunitjava.report.GraphSnapshot;
import dev.archunitjava.report.GraphSnapshotQuery;
import dev.archunitjava.report.HtmlGraphRenderer;
import dev.archunitjava.report.JsonGraphRenderer;
import dev.archunitjava.report.JunitXmlResultRenderer;
import dev.archunitjava.report.MermaidGraphRenderer;
import dev.archunitjava.report.ResultJsonRenderer;
import dev.archunitjava.report.SarifResultRenderer;
import dev.archunitjava.result.RuleStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable command dispatcher with no process, plugin, reflection, or target-code execution hooks. */
public final class CliRunner {
    public int run(String[] arguments, Appendable standardOut, Appendable standardError) {
        try {
            if (arguments == null || arguments.length == 0
                    || arguments.length == 1 && help(arguments[0])) {
                append(standardOut, usage());
                return CliExitCode.SUCCESS.code();
            }
            String command = arguments[0];
            Map<String, String> options = options(arguments);
            CliConfiguration configuration = CliConfigurationLoader.load(
                    requiredPath(options, "--config"), requiredPath(options, "--root"));
            if (options.containsKey("--result-format")) {
                configuration = configuration.withResultFormat(
                        CliResultFormat.parse(options.get("--result-format")));
            }
            if (options.containsKey("--graph-format")) {
                configuration = configuration.withGraphFormat(
                        CliGraphFormat.parse(options.get("--graph-format")));
            }
            return switch (command) {
                case "validate-config" -> validate(configuration, standardOut);
                case "explain" -> explain(configuration, standardOut);
                case "check" -> check(configuration, standardOut);
                case "graph" -> graph(configuration, standardOut);
                default -> throw new UsageException("Unknown command: " + command);
            };
        } catch (UsageException error) {
            append(standardError, "Usage error: " + error.getMessage() + "\n" + usage());
            return CliExitCode.USAGE.code();
        } catch (CliConfigurationException error) {
            append(standardError, "Configuration error: " + error.getMessage() + '\n');
            return CliExitCode.INVALID_CONFIGURATION.code();
        } catch (RuntimeException error) {
            append(standardError, "Analysis error: " + safeMessage(error) + '\n');
            return CliExitCode.ANALYSIS_ERROR.code();
        }
    }

    public static String usage() {
        return "Usage: archunitjava <check|graph|explain|validate-config> "
                + "--config <file> --root <approved-directory> "
                + "[--result-format console|json|sarif|junit-xml] "
                + "[--graph-format dot|mermaid|json|csv|d2|html]\n"
                + CliExitCode.documentation();
    }

    private static int validate(CliConfiguration configuration, Appendable out) {
        append(out, "configuration valid: " + CliConfiguration.SCHEMA + '\n');
        return CliExitCode.SUCCESS.code();
    }

    private static int explain(CliConfiguration configuration, Appendable out) {
        StringBuilder text = new StringBuilder("schema=").append(CliConfiguration.SCHEMA)
                .append("\ninputs=").append(configuration.inputs().size())
                .append("\nrules=").append(configuration.rules().size()).append('\n');
        for (CliRuleConfiguration rule : configuration.rules()) {
            text.append("rule ").append(rule.id()).append(": domain=")
                    .append(rule.domain()).append(" mode=").append(rule.mode())
                    .append(" origins=").append(singleLine(rule.origins().toString()))
                    .append(" targets=").append(singleLine(rule.targets().toString()))
                    .append(" self=").append(rule.selfDependencies())
                    .append(" external=").append(rule.externalDependencies()).append('\n');
        }
        append(out, text.toString());
        return CliExitCode.SUCCESS.code();
    }

    private static int check(CliConfiguration configuration, Appendable out) {
        CliAnalysisResult analysis = new CliAnalyzer().analyze(configuration);
        append(out, switch (configuration.resultFormat()) {
            case CONSOLE -> ConsoleResultRenderer.render(analysis.results());
            case JSON -> ResultJsonRenderer.render(analysis.results());
            case SARIF -> SarifResultRenderer.render(analysis.results());
            case JUNIT_XML -> JunitXmlResultRenderer.render(analysis.results());
        });
        if (analysis.results().results().stream().anyMatch(result ->
                result.status() == RuleStatus.INCOMPLETE || result.status() == RuleStatus.SKIPPED)) {
            return CliExitCode.ANALYSIS_ERROR.code();
        }
        return analysis.results().results().stream()
                .anyMatch(result -> result.status() == RuleStatus.FAILED)
                ? CliExitCode.POLICY_VIOLATION.code() : CliExitCode.SUCCESS.code();
    }

    private static int graph(CliConfiguration configuration, Appendable out) {
        CliAnalysisResult analysis = new CliAnalyzer().analyze(configuration);
        GraphSnapshot snapshot = switch (configuration.graphDomain()) {
            case TYPES -> GraphSnapshotQuery.types(analysis.graph()).snapshot();
            case PACKAGES -> GraphSnapshotQuery.packages(analysis.graph()).snapshot();
        };
        append(out, switch (configuration.graphFormat()) {
            case DOT -> DotGraphRenderer.render(snapshot);
            case MERMAID -> MermaidGraphRenderer.render(snapshot);
            case JSON -> JsonGraphRenderer.render(snapshot);
            case CSV -> CsvGraphRenderer.render(snapshot);
            case D2 -> D2GraphRenderer.render(snapshot);
            case HTML -> HtmlGraphRenderer.render(snapshot);
        });
        return CliExitCode.SUCCESS.code();
    }

    private static Map<String, String> options(String[] arguments) {
        if ((arguments.length - 1) % 2 != 0) {
            throw new UsageException("Every option requires one value");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 1; index < arguments.length; index += 2) {
            String option = arguments[index];
            if (!SetHolder.ALLOWED.contains(option)) {
                throw new UsageException("Unknown option: " + option);
            }
            if (result.putIfAbsent(option, arguments[index + 1]) != null) {
                throw new UsageException("Duplicate option: " + option);
            }
        }
        return Map.copyOf(result);
    }

    private static Path requiredPath(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new UsageException("Missing option " + name);
        return Path.of(value);
    }

    private static boolean help(String value) {
        return value.equals("help") || value.equals("--help") || value.equals("-h");
    }

    private static void append(Appendable destination, String value) {
        try {
            if (destination != null) destination.append(value);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return singleLine(message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message);
    }

    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static final class SetHolder {
        private static final java.util.Set<String> ALLOWED = java.util.Set.of(
                "--config", "--root", "--result-format", "--graph-format");
    }

    private static final class UsageException extends RuntimeException {
        private UsageException(String message) { super(message); }
    }
}
