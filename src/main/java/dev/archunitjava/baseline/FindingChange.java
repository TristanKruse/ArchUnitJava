package dev.archunitjava.baseline;

import java.util.Objects;
import java.util.Optional;

/** One deterministic comparison entry, retaining both sides when a finding moved. */
public record FindingChange(
        FindingState state,
        Optional<BaselineFinding> previous,
        Optional<BaselineFinding> current,
        Optional<String> suppressionId) implements Comparable<FindingChange> {
    public FindingChange {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(suppressionId, "suppressionId");
        if (previous.isEmpty() && current.isEmpty()) {
            throw new IllegalArgumentException("A finding change requires a finding");
        }
        if ((state == FindingState.SUPPRESSED || state == FindingState.EXPIRED)
                != suppressionId.isPresent()) {
            throw new IllegalArgumentException("Suppression state and id must agree");
        }
    }

    @Override
    public int compareTo(FindingChange other) {
        int result = state.compareTo(other.state);
        if (result != 0) return result;
        result = key(previous, current).compareTo(key(other.previous, other.current));
        return result != 0 ? result : suppressionId.orElse("").compareTo(
                other.suppressionId.orElse(""));
    }

    private static String key(
            Optional<BaselineFinding> previous, Optional<BaselineFinding> current) {
        return current.or(() -> previous).orElseThrow().exactFingerprint();
    }
}
