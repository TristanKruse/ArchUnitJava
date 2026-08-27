package dev.archunitjava.report;

/** Deterministic output bounds; omitted counts remain in snapshot metadata. */
public record GraphSnapshotLimits(int maxNodes, int maxEdges, int maxEvidencePerEdge) {
    public GraphSnapshotLimits {
        positive(maxNodes, "maxNodes");
        positive(maxEdges, "maxEdges");
        positive(maxEvidencePerEdge, "maxEvidencePerEdge");
    }

    public static GraphSnapshotLimits defaults() {
        return new GraphSnapshotLimits(100_000, 1_000_000, 10_000);
    }

    private static void positive(int value, String role) {
        if (value <= 0) throw new IllegalArgumentException(role + " must be positive");
    }
}
