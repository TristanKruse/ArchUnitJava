package dev.archunitjava.report;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Deterministic D2 rendering with generated identifiers and quoted target strings. */
public final class D2GraphRenderer {
    private D2GraphRenderer() {}

    public static String render(GraphSnapshot snapshot) {
        GraphSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        Map<String, String> aliases = ReportAliases.nodes(value);
        StringBuilder result = new StringBuilder();
        result.append("# schema: ").append(value.schemaVersion()).append('\n');
        result.append("# domain: ").append(value.query().domain().name()).append('\n');
        result.append("# truncated: ").append(value.query().truncated()).append('\n');
        result.append("# omitted-nodes: ").append(value.query().omittedNodeCount()).append('\n');
        result.append("# omitted-edges: ").append(value.query().omittedEdgeCount()).append('\n');
        result.append("# omitted-evidence: ").append(value.query().omittedEvidenceCount()).append('\n');
        for (SnapshotNode node : value.nodes()) {
            String label = node.label() + " (" + node.id()
                    + "; sources: " + node.sourceNodeCount() + ")";
            result.append(aliases.get(node.id())).append(": \"")
                    .append(ReportEscapes.d2(label)).append("\"\n");
        }
        for (SnapshotEdge edge : value.edges()) {
            String label = "dependencies: " + edge.sourceEdgeCount()
                    + "; evidence: " + edge.evidenceCount()
                    + "; omitted evidence: " + edge.omittedEvidenceCount();
            result.append(aliases.get(edge.originId())).append(" -> ")
                    .append(aliases.get(edge.targetId())).append(": \"")
                    .append(ReportEscapes.d2(label)).append("\"\n");
        }
        return result.toString();
    }

    public static byte[] renderBytes(GraphSnapshot snapshot) {
        return render(snapshot).getBytes(StandardCharsets.UTF_8);
    }
}
