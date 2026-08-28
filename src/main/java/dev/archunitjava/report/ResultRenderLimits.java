package dev.archunitjava.report;

/** Explicit bounds for human-oriented result renderers. */
public record ResultRenderLimits(
        int maxViolationsPerRule, int maxEvidencePerViolation, int maxDiagnosticsPerRule) {
    public ResultRenderLimits {
        if (maxViolationsPerRule < 0 || maxEvidencePerViolation < 0
                || maxDiagnosticsPerRule < 0) {
            throw new IllegalArgumentException("Result render limits must not be negative");
        }
    }

    public static ResultRenderLimits defaults() {
        return new ResultRenderLimits(100, 20, 100);
    }
}
