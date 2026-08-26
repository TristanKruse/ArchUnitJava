package dev.archunitjava.rules;

import dev.archunitjava.execution.Checkable;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import java.util.Collection;

/** Immutable architecture rule with uniform metadata decoration. */
public interface ArchitectureRule extends Checkable<RuleResult> {
    RuleMetadata metadata();

    ArchitectureRule as(String displayName);

    ArchitectureRule because(String rationale);

    ArchitectureRule tagged(String... tags);

    ArchitectureRule tagged(Collection<String> tags);

    ArchitectureRule withSeverity(Severity severity);
}
