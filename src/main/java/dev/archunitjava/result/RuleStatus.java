package dev.archunitjava.result;

/** Mutually exclusive terminal states of one architecture rule evaluation. */
public enum RuleStatus {
    PASSED,
    FAILED,
    SKIPPED,
    INCOMPLETE
}
