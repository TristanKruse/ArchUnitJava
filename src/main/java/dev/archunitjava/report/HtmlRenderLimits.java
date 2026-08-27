package dev.archunitjava.report;

/** Explicit bounds for self-contained interactive HTML generation. */
public record HtmlRenderLimits(
        int maxNodes, int maxEdges, int maxEvidenceItems, int maxTargetCharacters) {
    public HtmlRenderLimits {
        positive(maxNodes, "maxNodes");
        positive(maxEdges, "maxEdges");
        positive(maxEvidenceItems, "maxEvidenceItems");
        positive(maxTargetCharacters, "maxTargetCharacters");
    }

    public static HtmlRenderLimits defaults() {
        return new HtmlRenderLimits(2_000, 10_000, 50_000, 5_000_000);
    }

    private static void positive(int value, String role) {
        if (value <= 0) throw new IllegalArgumentException(role + " must be positive");
    }
}
