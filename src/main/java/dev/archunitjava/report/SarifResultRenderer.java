package dev.archunitjava.report;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.result.Violation;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Deterministic SARIF 2.1.0 rendering with repository-relative artifact locations. */
public final class SarifResultRenderer {
    private SarifResultRenderer() {}

    public static String render(ResultReport report) {
        ResultReport value = Objects.requireNonNull(report, "report");
        StringBuilder out = new StringBuilder();
        out.append("{\"$schema\":\"https://json.schemastore.org/sarif-2.1.0.json\",")
                .append("\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{")
                .append("\"name\":\"ArchUnitJava\",\"rules\":[");
        for (int index = 0; index < value.results().size(); index++) {
            if (index > 0) out.append(',');
            rule(out, value.results().get(index).metadata());
        }
        out.append("]}},\"results\":[");
        int emitted = 0;
        for (RuleResult result : value.results()) {
            for (Violation violation : result.violations()) {
                if (emitted++ > 0) out.append(',');
                violation(out, result.metadata(), violation);
            }
        }
        out.append("],\"invocations\":[{\"executionSuccessful\":")
                .append(value.results().stream().noneMatch(r -> r.status() == RuleStatus.INCOMPLETE));
        out.append(",\"toolExecutionNotifications\":[");
        int notification = 0;
        for (RuleResult result : value.results()) {
            if (result.status() != RuleStatus.INCOMPLETE) continue;
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (notification++ > 0) out.append(',');
                diagnostic(out, result, diagnostic);
            }
        }
        return out.append("]}]}]}\n").toString();
    }

    public static byte[] renderBytes(ResultReport report) {
        return render(report).getBytes(StandardCharsets.UTF_8);
    }

    private static void rule(StringBuilder out, RuleMetadata metadata) {
        out.append('{');
        ResultRenderSupport.jsonField(out, "id", metadata.semanticIdentity()).append(',');
        ResultRenderSupport.jsonField(out, "name", metadata.displayName()).append(',');
        out.append("\"shortDescription\":{");
        ResultRenderSupport.jsonField(out, "text", metadata.description());
        out.append("},\"properties\":{");
        ResultRenderSupport.jsonField(out, "presentationIdentity", metadata.presentationIdentity())
                .append(',');
        ResultRenderSupport.jsonField(out, "severity", metadata.severity().name()).append(',');
        out.append("\"rationale\":");
        if (metadata.rationale().isPresent()) {
            out.append('"').append(ReportEscapes.json(metadata.rationale().orElseThrow())).append('"');
        } else {
            out.append("null");
        }
        out.append(",\"tags\":");
        ResultRenderSupport.jsonStringArray(out, metadata.tags());
        out.append("}}");
    }

    private static void violation(
            StringBuilder out, RuleMetadata metadata, Violation violation) {
        out.append('{');
        ResultRenderSupport.jsonField(out, "ruleId", metadata.semanticIdentity()).append(',');
        ResultRenderSupport.jsonField(out, "level", level(violation.severity())).append(',');
        out.append("\"message\":{");
        ResultRenderSupport.jsonField(out, "text", violation.code() + ": " + violation.id().value());
        out.append("},\"properties\":{");
        ResultRenderSupport.jsonField(out, "violationId", violation.id().value()).append(',');
        ResultRenderSupport.jsonField(out, "violationCode", violation.code()).append(',');
        ResultRenderSupport.jsonField(out, "presentationIdentity", metadata.presentationIdentity());
        out.append("},\"locations\":[");
        for (int index = 0; index < violation.evidence().size(); index++) {
            if (index > 0) out.append(',');
            location(out, violation.evidence().get(index));
        }
        out.append("]}");
    }

    private static void location(StringBuilder out, DependencyEvidence evidence) {
        out.append("{\"physicalLocation\":{\"artifactLocation\":{");
        ResultRenderSupport.jsonField(out, "uri", ResultRenderSupport.repositoryUri(
                evidence.location().resourcePath())).append(',');
        ResultRenderSupport.jsonField(out, "uriBaseId", "%SRCROOT%");
        out.append('}');
        if (evidence.lineNumber().isPresent() && evidence.lineNumber().getAsInt() > 0) {
            out.append(",\"region\":{\"startLine\":")
                    .append(evidence.lineNumber().getAsInt()).append('}');
        }
        out.append("},\"properties\":{");
        out.append("\"sourceFile\":");
        if (evidence.sourceFile().isPresent()) {
            out.append('"').append(ReportEscapes.json(evidence.sourceFile().orElseThrow())).append('"');
        } else {
            out.append("null");
        }
        out.append("}}");
    }

    private static void diagnostic(StringBuilder out, RuleResult result, Diagnostic diagnostic) {
        out.append('{');
        ResultRenderSupport.jsonField(out, "descriptor", result.ruleId() + ":" + diagnostic.code())
                .append(',');
        ResultRenderSupport.jsonField(out, "level", level(diagnostic.severity())).append(',');
        out.append("\"message\":{");
        ResultRenderSupport.jsonField(out, "text",
                "Analysis error " + diagnostic.code() + " for " + result.ruleId());
        out.append("},\"properties\":{");
        ResultRenderSupport.jsonField(out, "kind", "analysis-error").append(',');
        out.append("\"context\":");
        ResultRenderSupport.jsonMap(out, diagnostic.context());
        out.append("}}");
    }

    private static String level(dev.archunitjava.result.Severity severity) {
        return switch (severity) {
            case INFO -> "note";
            case WARNING -> "warning";
            case ERROR -> "error";
        };
    }
}
