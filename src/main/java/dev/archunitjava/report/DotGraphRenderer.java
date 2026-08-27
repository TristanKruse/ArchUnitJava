package dev.archunitjava.report;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Deterministic DOT rendering with quoted attributes and generated identifiers only. */
public final class DotGraphRenderer {
    private DotGraphRenderer() {}

    public static String render(GraphSnapshot snapshot) {
        GraphSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        Map<String, String> aliases = ReportAliases.nodes(value);
        SnapshotQueryMetadata query = value.query();
        StringBuilder result = new StringBuilder();
        result.append("digraph architecture {\n");
        result.append("  graph [charset=\"UTF-8\", snapshot_schema=\"")
                .append(ReportEscapes.dot(value.schemaVersion()))
                .append("\", report_domain=\"")
                .append(query.domain().name())
                .append("\", snapshot_truncated=\"")
                .append(query.truncated())
                .append("\", omitted_nodes=\"")
                .append(query.omittedNodeCount())
                .append("\", omitted_edges=\"")
                .append(query.omittedEdgeCount())
                .append("\", omitted_evidence=\"")
                .append(query.omittedEvidenceCount())
                .append("\"];\n");
        for (SnapshotNode node : value.nodes()) {
            result.append("  ").append(aliases.get(node.id()))
                    .append(" [label=\"").append(ReportEscapes.dot(node.label()))
                    .append("\", stable_id=\"").append(ReportEscapes.dot(node.id()))
                    .append("\", source_node_count=\"").append(node.sourceNodeCount())
                    .append("\"];\n");
        }
        for (SnapshotEdge edge : value.edges()) {
            result.append("  ").append(aliases.get(edge.originId()))
                    .append(" -> ").append(aliases.get(edge.targetId()))
                    .append(" [label=\"dependencies: ").append(edge.sourceEdgeCount())
                    .append("; evidence: ").append(edge.evidenceCount())
                    .append("\", stable_id=\"").append(ReportEscapes.dot(edge.id()))
                    .append("\", dependency_kinds=\"")
                    .append(ReportEscapes.dot(String.join(",", edge.dependencyKinds())))
                    .append("\", source_edge_count=\"").append(edge.sourceEdgeCount())
                    .append("\", evidence_count=\"").append(edge.evidenceCount())
                    .append("\", omitted_evidence=\"").append(edge.omittedEvidenceCount())
                    .append("\"];\n");
        }
        return result.append("}\n").toString();
    }

    public static byte[] renderBytes(GraphSnapshot snapshot) {
        return render(snapshot).getBytes(StandardCharsets.UTF_8);
    }
}
