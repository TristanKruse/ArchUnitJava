package dev.archunitjava.rules;

import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleResult;
import java.util.List;

/** Builds the ordinary result while retaining diagnostics supplied by the shared terminal. */
@FunctionalInterface
public interface RuleResultFactory {
    RuleResult create(List<Diagnostic> terminalDiagnostics);
}
