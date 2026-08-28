package dev.archunitjava.metrics;

/** Whether a formula was evaluated or deliberately withheld. */
public enum MetricAvailability {
    COMPUTED,
    NOT_APPLICABLE,
    INCOMPLETE_EVIDENCE
}
