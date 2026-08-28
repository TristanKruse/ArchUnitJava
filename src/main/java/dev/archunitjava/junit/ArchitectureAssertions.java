package dev.archunitjava.junit;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.execution.Checkable;
import dev.archunitjava.execution.ExecutionError;
import dev.archunitjava.report.ConsoleResultRenderer;
import dev.archunitjava.report.ResultRenderLimits;
import dev.archunitjava.report.ResultReport;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.RuleStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Dependency-free assertions recognized naturally by JUnit Jupiter and other Java test runners. */
public final class ArchitectureAssertions {
    private ArchitectureAssertions() {}

    public static void assertPasses(Checkable<RuleResult> checkable) {
        assertPasses(checkable, CheckOptions.defaults(), ResultRenderLimits.defaults());
    }

    public static void assertPasses(
            Checkable<RuleResult> checkable, CheckOptions options) {
        assertPasses(checkable, options, ResultRenderLimits.defaults());
    }

    public static void assertPasses(
            Checkable<RuleResult> checkable, ResultRenderLimits limits) {
        assertPasses(checkable, CheckOptions.defaults(), limits);
    }

    public static void assertPasses(
            Checkable<RuleResult> checkable,
            CheckOptions options,
            ResultRenderLimits limits) {
        Objects.requireNonNull(checkable, "checkable");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(limits, "limits");
        RuleResult result;
        try {
            result = checkable.check(options);
        } catch (ExecutionError error) {
            throw analysisFailure(
                    "Architecture analysis failed [" + error.code() + "]: " + error.getMessage(),
                    Optional.empty(), Optional.of(error.code()), error.context(), error);
        } catch (RuntimeException error) {
            throw analysisFailure(
                    "Architecture analysis failed unexpectedly: " + safeMessage(error),
                    Optional.empty(), Optional.of("analysis.unexpected"), Map.of(), error);
        }
        if (result == null) {
            throw analysisFailure(
                    "Architecture analysis returned no result",
                    Optional.empty(), Optional.of("analysis.null-result"), Map.of(), null);
        }
        if (result.status() == RuleStatus.PASSED) return;

        String rendered = ConsoleResultRenderer.render(
                ResultReport.of(List.of(result)), limits).stripTrailing();
        if (result.status() == RuleStatus.FAILED) {
            throw new ArchitecturePolicyAssertionError(rendered, result);
        }
        String reason = result.status() == RuleStatus.SKIPPED
                ? "Architecture rule did not execute\n" : "Architecture analysis was incomplete\n";
        throw analysisFailure(reason + rendered, Optional.of(result),
                Optional.of(result.status() == RuleStatus.SKIPPED
                        ? "analysis.skipped" : "analysis.incomplete"), Map.of(), null);
    }

    private static ArchitectureAnalysisAssertionError analysisFailure(
            String message,
            Optional<RuleResult> result,
            Optional<String> code,
            Map<String, String> context,
            Throwable cause) {
        return new ArchitectureAnalysisAssertionError(
                message, result, code, context, cause);
    }

    private static String safeMessage(RuntimeException error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getName() : error.getMessage();
    }
}
