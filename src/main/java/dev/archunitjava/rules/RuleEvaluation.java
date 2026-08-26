package dev.archunitjava.rules;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;

/** Pure rule terminal supplied with the immutable metadata that must be attached to its result. */
@FunctionalInterface
public interface RuleEvaluation {
    RuleResult evaluate(RuleMetadata metadata, CheckOptions options);
}
