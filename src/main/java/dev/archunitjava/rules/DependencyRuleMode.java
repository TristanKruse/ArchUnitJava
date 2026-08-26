package dev.archunitjava.rules;

/** Shared dependency assertion moods evaluated by one graph engine. */
public enum DependencyRuleMode {
    /** No selected origin may have a matching dependency. */
    NO,
    /** Every considered dependency from a selected origin must match. */
    ONLY,
    /** The selected origin set must contain at least one matching dependency. */
    ANY,
    /** Every selected origin must contain at least one matching dependency. */
    REQUIRED
}
