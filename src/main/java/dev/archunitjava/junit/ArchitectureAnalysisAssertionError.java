package dev.archunitjava.junit;

import dev.archunitjava.result.RuleResult;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Assertion failure caused by incomplete or failed analysis rather than policy violations. */
public final class ArchitectureAnalysisAssertionError extends AssertionError {
    private final Optional<RuleResult> result;
    private final Optional<String> executionCode;
    private final Map<String, String> context;

    ArchitectureAnalysisAssertionError(
            String message,
            Optional<RuleResult> result,
            Optional<String> executionCode,
            Map<String, String> context,
            Throwable cause) {
        super(message);
        this.result = Objects.requireNonNull(result, "result");
        this.executionCode = Objects.requireNonNull(executionCode, "executionCode");
        this.context = Collections.unmodifiableMap(
                new TreeMap<>(Objects.requireNonNull(context, "context")));
        if (cause != null) initCause(cause);
    }

    public Optional<RuleResult> result() {
        return result;
    }

    public Optional<String> executionCode() {
        return executionCode;
    }

    public Map<String, String> context() {
        return context;
    }
}
