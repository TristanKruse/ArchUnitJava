package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Conservative generated-code decision with reviewable supporting signals. */
public record GeneratedCodeClassification(boolean generated, List<GeneratedCodeSignal> signals) {
    public GeneratedCodeClassification {
        Objects.requireNonNull(signals, "signals");
        signals = signals.stream().distinct().sorted().toList();
        if (generated && signals.size() < 2) {
            throw new IllegalArgumentException("generated classification requires two independent signals");
        }
    }
}
