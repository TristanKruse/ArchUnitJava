package dev.archunitjava.report;

import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.result.Violation;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Deterministic JUnit XML rendering with policy failures and analysis errors kept separate. */
public final class JunitXmlResultRenderer {
    private JunitXmlResultRenderer() {}

    public static String render(ResultReport report) {
        return render(report, ResultRenderLimits.defaults());
    }

    public static String render(ResultReport report, ResultRenderLimits limits) {
        ResultReport value = Objects.requireNonNull(report, "report");
        ResultRenderLimits bounds = Objects.requireNonNull(limits, "limits");
        long failures = value.results().stream().filter(r -> r.status() == RuleStatus.FAILED).count();
        long errors = value.results().stream().filter(r -> r.status() == RuleStatus.INCOMPLETE).count();
        long skipped = value.results().stream().filter(r -> r.status() == RuleStatus.SKIPPED).count();
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<testsuite name=\"ArchUnitJava\" tests=\"").append(value.results().size())
                .append("\" failures=\"").append(failures).append("\" errors=\"").append(errors)
                .append("\" skipped=\"").append(skipped).append("\" time=\"0\">\n");
        for (RuleResult result : value.results()) testcase(out, result, bounds);
        return out.append("</testsuite>\n").toString();
    }

    public static byte[] renderBytes(ResultReport report) {
        return render(report).getBytes(StandardCharsets.UTF_8);
    }

    private static void testcase(
            StringBuilder out, RuleResult result, ResultRenderLimits limits) {
        out.append("  <testcase classname=\"architecture\" name=\"")
                .append(ResultRenderSupport.xml(result.metadata().displayName()))
                .append("\" time=\"0\">\n");
        switch (result.status()) {
            case FAILED -> {
                out.append("    <failure type=\"policy-violation\" message=\"")
                        .append(result.violations().size()).append(" architecture violation(s)\">");
                out.append(ResultRenderSupport.xml(violations(result, limits)));
                out.append("</failure>\n");
            }
            case INCOMPLETE -> {
                out.append("    <error type=\"analysis-error\" message=\"Architecture analysis incomplete\">");
                out.append(ResultRenderSupport.xml(diagnostics(result, limits)));
                out.append("</error>\n");
            }
            case SKIPPED -> out.append("    <skipped message=\"Architecture rule skipped\"/>\n");
            case PASSED -> { }
        }
        if (!result.diagnostics().isEmpty() && result.status() != RuleStatus.INCOMPLETE) {
            out.append("    <system-out>")
                    .append(ResultRenderSupport.xml(diagnostics(result, limits)))
                    .append("</system-out>\n");
        }
        out.append("  </testcase>\n");
    }

    private static String violations(RuleResult result, ResultRenderLimits limits) {
        StringBuilder out = new StringBuilder();
        int count = Math.min(result.violations().size(), limits.maxViolationsPerRule());
        for (int index = 0; index < count; index++) {
            Violation violation = result.violations().get(index);
            out.append(violation.id().value()).append(" [").append(violation.severity())
                    .append("] ").append(violation.code()).append('\n');
            int evidence = Math.min(violation.evidence().size(), limits.maxEvidencePerViolation());
            for (int item = 0; item < evidence; item++) {
                out.append("  at ").append(violation.evidence().get(item).location().resourcePath());
                violation.evidence().get(item).lineNumber().ifPresent(line -> out.append(':').append(line));
                out.append('\n');
            }
            omitted(out, "evidence", violation.evidence().size() - evidence);
        }
        omitted(out, "violations", result.violations().size() - count);
        return out.toString();
    }

    private static String diagnostics(RuleResult result, ResultRenderLimits limits) {
        StringBuilder out = new StringBuilder();
        int count = Math.min(result.diagnostics().size(), limits.maxDiagnosticsPerRule());
        for (int index = 0; index < count; index++) {
            Diagnostic diagnostic = result.diagnostics().get(index);
            out.append(diagnostic.code()).append(" [").append(diagnostic.severity()).append("] ")
                    .append(diagnostic.context()).append('\n');
        }
        omitted(out, "diagnostics", result.diagnostics().size() - count);
        return out.toString();
    }

    private static void omitted(StringBuilder out, String kind, int count) {
        if (count > 0) out.append("... ").append(count).append(" more ").append(kind).append('\n');
    }
}
