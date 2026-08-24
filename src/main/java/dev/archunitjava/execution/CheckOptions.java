package dev.archunitjava.execution;

import java.util.Objects;

/** Immutable policies applied uniformly by architecture-rule terminals. */
public final class CheckOptions {
    private static final CheckOptions DEFAULTS = new Builder().build();

    private final boolean allowEmptySelection;
    private final boolean allowIncompleteAnalysis;

    private CheckOptions(Builder builder) {
        allowEmptySelection = builder.allowEmptySelection;
        allowIncompleteAnalysis = builder.allowIncompleteAnalysis;
    }

    /** Returns the deterministic strict defaults used by {@link Checkable#check()}. */
    public static CheckOptions defaults() {
        return DEFAULTS;
    }

    /** Starts a builder initialized with the deterministic strict defaults. */
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public boolean allowEmptySelection() {
        return allowEmptySelection;
    }

    public boolean allowIncompleteAnalysis() {
        return allowIncompleteAnalysis;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CheckOptions options
                && allowEmptySelection == options.allowEmptySelection
                && allowIncompleteAnalysis == options.allowIncompleteAnalysis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowEmptySelection, allowIncompleteAnalysis);
    }

    @Override
    public String toString() {
        return "CheckOptions[allowEmptySelection=" + allowEmptySelection
                + ", allowIncompleteAnalysis=" + allowIncompleteAnalysis + ']';
    }

    /** Mutable construction state; built options never retain a reference to it. */
    public static final class Builder {
        private boolean allowEmptySelection;
        private boolean allowIncompleteAnalysis;

        private Builder() {}

        private Builder(CheckOptions options) {
            allowEmptySelection = options.allowEmptySelection;
            allowIncompleteAnalysis = options.allowIncompleteAnalysis;
        }

        public Builder allowEmptySelection(boolean value) {
            allowEmptySelection = value;
            return this;
        }

        public Builder allowIncompleteAnalysis(boolean value) {
            allowIncompleteAnalysis = value;
            return this;
        }

        public CheckOptions build() {
            return new CheckOptions(this);
        }
    }
}
