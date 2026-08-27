package dev.archunitjava.report;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Deterministic Mermaid rendering without directives, links, or target-controlled identifiers. */
public final class MermaidGraphRenderer {
    private MermaidGraphRenderer() {}

    public static String render(GraphSnapshot snapshot) {
        GraphSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        Map<String, String> aliases = ReportAliases.nodes(value);
        SnapshotQueryMetadata query = value.query();
        StringBuilder result = new StringBuilder();
        result.append("%% schema: ").append(value.schemaVersion()).append('\n');
        result.append("%% domain: ").append(query.domain().name()).append('\n');
        result.append("%% truncated: ").append(query.truncated()).append('\n');
        result.append("%% omitted-nodes: ").append(query.omittedNodeCount()).append('\n');
        result.append("%% omitted-edges: ").append(query.omittedEdgeCount()).append('\n');
        result.append("%% omitted-evidence: ").append(query.omittedEvidenceCount()).append('\n');
        result.append("flowchart LR\n");
        for (SnapshotNode node : value.nodes()) {
            String label = node.label() + " (" + node.id()
                    + "; sources: " + node.sourceNodeCount() + ")";
            result.append("  ").append(aliases.get(node.id()))
                    .append("[\"").append(ReportEscapes.mermaid(label)).append("\"]\n");
        }
        for (SnapshotEdge edge : value.edges()) {
            String label = "dependencies: " + edge.sourceEdgeCount()
                    + "; evidence: " + edge.evidenceCount()
                    + "; omitted evidence: " + edge.omittedEvidenceCount();
            result.append("  ").append(aliases.get(edge.originId()))
                    .append(" -->|\"").append(ReportEscapes.mermaid(label)).append("\"| ")
                    .append(aliases.get(edge.targetId())).append('\n');
        }
        return result.toString();
    }

    public static byte[] renderBytes(GraphSnapshot snapshot) {
        return render(snapshot).getBytes(StandardCharsets.UTF_8);
    }
}
