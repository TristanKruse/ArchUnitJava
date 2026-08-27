package dev.archunitjava.report;

import java.util.List;
import java.util.Objects;

/** One detached aggregated edge with retained source-edge and evidence counts. */
public record SnapshotEdge(
        String id,
        String originId,
        String targetId,
        List<String> dependencyKinds,
        int sourceEdgeCount,
        List<String> sourceEdges,
        int evidenceCount,
        List<SnapshotEvidence> evidence,
        int omittedEvidenceCount)
        implements Comparable<SnapshotEdge> {
    public SnapshotEdge {
        id = text(id, "id");
        originId = text(originId, "originId");
        targetId = text(targetId, "targetId");
        Objects.requireNonNull(dependencyKinds, "dependencyKinds");
        dependencyKinds = dependencyKinds.stream()
                .map(value -> text(value, "dependencyKind"))
                .distinct().sorted().toList();
        if (dependencyKinds.isEmpty()) throw new IllegalArgumentException("dependencyKinds must not be empty");
        if (sourceEdgeCount <= 0) throw new IllegalArgumentException("sourceEdgeCount must be positive");
        Objects.requireNonNull(sourceEdges, "sourceEdges");
        sourceEdges = sourceEdges.stream()
                .map(value -> text(value, "sourceEdge"))
                .distinct().sorted().toList();
        if (sourceEdges.size() != sourceEdgeCount) {
            throw new IllegalArgumentException("sourceEdgeCount disagrees with sourceEdges");
        }
        if (evidenceCount < 0) throw new IllegalArgumentException("evidenceCount must not be negative");
        Objects.requireNonNull(evidence, "evidence");
        evidence = evidence.stream()
                .map(value -> Objects.requireNonNull(value, "evidence item"))
                .distinct().sorted().toList();
        if (omittedEvidenceCount < 0
                || evidence.size() + omittedEvidenceCount != evidenceCount) {
            throw new IllegalArgumentException("Evidence counts disagree");
        }
    }

    @Override
    public int compareTo(SnapshotEdge other) {
        return id.compareTo(other.id);
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
