package dev.archunitjava.report;

import java.util.List;
import java.util.Objects;

/** One detached collapsed node and the source nodes it represents. */
public record SnapshotNode(
        String id, String label, int sourceNodeCount, List<String> sourceNodeIds)
        implements Comparable<SnapshotNode> {
    public SnapshotNode {
        id = text(id, "id");
        label = text(label, "label");
        if (sourceNodeCount <= 0) throw new IllegalArgumentException("sourceNodeCount must be positive");
        Objects.requireNonNull(sourceNodeIds, "sourceNodeIds");
        sourceNodeIds = sourceNodeIds.stream()
                .map(value -> text(value, "sourceNodeId"))
                .distinct().sorted().toList();
        if (sourceNodeIds.size() != sourceNodeCount) {
            throw new IllegalArgumentException("sourceNodeCount disagrees with sourceNodeIds");
        }
    }

    @Override
    public int compareTo(SnapshotNode other) {
        return id.compareTo(other.id);
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
