package dev.archunitjava.junit;

import dev.archunitjava.result.RuleResult;
import java.util.Objects;

/** Assertion failure caused by architecture policy violations, not analysis failure. */
public final class ArchitecturePolicyAssertionError extends AssertionError {
    private final RuleResult result;

    ArchitecturePolicyAssertionError(String message, RuleResult result) {
        super(message);
        this.result = Objects.requireNonNull(result, "result");
    }

    public RuleResult result() {
        return result;
    }
}
