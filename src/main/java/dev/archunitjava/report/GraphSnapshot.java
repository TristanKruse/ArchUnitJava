package dev.archunitjava.report;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Versioned immutable graph snapshot consumed verbatim by all report renderers. */
public record GraphSnapshot(
        String schemaVersion,
        SnapshotQueryMetadata query,
        List<SnapshotNode> nodes,
        List<SnapshotEdge> edges) {
    public static final String SCHEMA_VERSION = "archunitjava.graph-snapshot.v1";

    public GraphSnapshot {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported graph snapshot schema: " + schemaVersion);
        }
        Objects.requireNonNull(query, "query");
        nodes = stable(nodes, "node");
        edges = stable(edges, "edge");
        Set<String> nodeIds = nodes.stream().map(SnapshotNode::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (edges.stream().anyMatch(edge ->
                !nodeIds.contains(edge.originId()) || !nodeIds.contains(edge.targetId()))) {
            throw new IllegalArgumentException("Snapshot edge refers to an omitted node");
        }
        if (nodes.size() + query.omittedNodeCount() != query.matchedNodeCount()) {
            throw new IllegalArgumentException("Snapshot node counts disagree with query metadata");
        }
        if (edges.size() + query.omittedEdgeCount() != query.matchedEdgeCount()) {
            throw new IllegalArgumentException("Snapshot edge counts disagree with query metadata");
        }
        if (edges.stream().mapToInt(SnapshotEdge::omittedEvidenceCount).sum()
                != query.omittedEvidenceCount()) {
            throw new IllegalArgumentException("Snapshot evidence counts disagree with query metadata");
        }
    }

    public GraphSnapshot(SnapshotQueryMetadata query, List<SnapshotNode> nodes, List<SnapshotEdge> edges) {
        this(SCHEMA_VERSION, query, nodes, edges);
    }

    private static <T extends Comparable<? super T>> List<T> stable(
            List<T> values, String role) {
        Objects.requireNonNull(values, role + "s");
        TreeSet<T> result = new TreeSet<>();
        for (T value : values) {
            if (!result.add(Objects.requireNonNull(value, role))) {
                throw new IllegalArgumentException("Duplicate snapshot " + role);
            }
        }
        return List.copyOf(result);
    }
}
