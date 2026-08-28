package dev.archunitjava.baseline;

import java.util.Objects;

/** Proposed explicit baseline update plus its reviewable comparison. */
public record BaselineUpdate(BaselineComparison comparison, ReviewedBaseline proposedBaseline) {
    public BaselineUpdate {
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(proposedBaseline, "proposedBaseline");
    }

    public String deterministicDiff() {
        return BaselineDiffRenderer.render(comparison);
    }
}
