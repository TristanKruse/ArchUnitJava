package dev.archunitjava.report;

import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Canonical, lossless JSON rendering of architecture-rule results. */
public final class ResultJsonRenderer {
    private ResultJsonRenderer() {}

    public static String render(ResultReport report) {
        ResultReport value = Objects.requireNonNull(report, "report");
        StringBuilder out = new StringBuilder();
        out.append('{');
        ResultRenderSupport.jsonField(out, "schemaVersion", value.schemaVersion()).append(',');
        out.append("\"results\":[");
        for (int index = 0; index < value.results().size(); index++) {
            if (index > 0) out.append(',');
            result(out, value.results().get(index));
        }
        return out.append("]}\n").toString();
    }

    public static byte[] renderBytes(ResultReport report) {
        return render(report).getBytes(StandardCharsets.UTF_8);
    }

    private static void result(StringBuilder out, RuleResult result) {
        out.append('{');
        metadata(out, result.metadata());
        out.append(',');
        ResultRenderSupport.jsonField(out, "status", result.status().name()).append(',');
        out.append("\"violations\":[");
        for (int index = 0; index < result.violations().size(); index++) {
            if (index > 0) out.append(',');
            ResultRenderSupport.jsonViolation(out, result.violations().get(index));
        }
        out.append("],\"diagnostics\":[");
        for (int index = 0; index < result.diagnostics().size(); index++) {
            if (index > 0) out.append(',');
            ResultRenderSupport.jsonDiagnostic(out, result.diagnostics().get(index));
        }
        out.append("]}");
    }

    private static void metadata(StringBuilder out, RuleMetadata metadata) {
        out.append("\"rule\":{");
        ResultRenderSupport.jsonField(out, "semanticIdentity", metadata.semanticIdentity()).append(',');
        ResultRenderSupport.jsonField(out, "presentationIdentity", metadata.presentationIdentity()).append(',');
        ResultRenderSupport.jsonField(out, "displayName", metadata.displayName()).append(',');
        ResultRenderSupport.jsonField(out, "description", metadata.description()).append(',');
        ResultRenderSupport.jsonOptionalField(out, "rationale", metadata.rationale()).append(',');
        out.append("\"tags\":");
        ResultRenderSupport.jsonStringArray(out, metadata.tags());
        out.append(',');
        ResultRenderSupport.jsonField(out, "severity", metadata.severity().name());
        out.append('}');
    }
}
