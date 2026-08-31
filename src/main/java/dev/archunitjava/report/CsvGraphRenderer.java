package dev.archunitjava.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Typed-row CSV rendering of query metadata, nodes, edges, provenance, and evidence. */
public final class CsvGraphRenderer {
    private static final List<String> HEADER = List.of(
            "schema_version", "record_type", "id", "origin_id", "target_id", "label",
            "dependency_kinds", "source_node_count", "source_edge_count", "evidence_count",
            "omitted_evidence_count", "location_id", "owner_member_id", "bytecode_offset",
            "source_file", "line_number", "metadata_key", "metadata_value");

    private CsvGraphRenderer() {}

    /**
     * Renders spreadsheet-safe CSV. Cells that spreadsheet programs could interpret as formulas
     * are prefixed with an apostrophe. Use {@link #renderMachineReadable(GraphSnapshot)} only for
     * lossless machine interchange that will not be opened as a spreadsheet.
     */
    public static String render(GraphSnapshot snapshot) {
        return neutralizeFormulas(renderMachineReadable(snapshot));
    }

    /** Renders lossless machine-readable CSV without spreadsheet formula neutralization. */
    public static String renderMachineReadable(GraphSnapshot snapshot) {
        GraphSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        StringBuilder result = new StringBuilder();
        row(result, HEADER);
        metadata(result, value, "domain", value.query().domain().name());
        metadataList(result, value, "includedKind", value.query().includedKinds());
        metadataList(result, value, "includedNodeId", value.query().includedNodeIds());
        metadataList(result, value, "excludedNodeId", value.query().excludedNodeIds());
        metadata(result, value, "retainSelfEdges", Boolean.toString(value.query().retainSelfEdges()));
        metadata(result, value, "mappingCount", Integer.toString(value.query().mappingCount()));
        metadata(result, value, "sourceNodeCount", Integer.toString(value.query().sourceNodeCount()));
        metadata(result, value, "sourceEdgeCount", Integer.toString(value.query().sourceEdgeCount()));
        metadata(result, value, "mappedSourceNodeCount", Integer.toString(value.query().mappedSourceNodeCount()));
        metadata(result, value, "matchedNodeCount", Integer.toString(value.query().matchedNodeCount()));
        metadata(result, value, "matchedEdgeCount", Integer.toString(value.query().matchedEdgeCount()));
        metadata(result, value, "omittedNodeCount", Integer.toString(value.query().omittedNodeCount()));
        metadata(result, value, "omittedEdgeCount", Integer.toString(value.query().omittedEdgeCount()));
        metadata(result, value, "omittedEvidenceCount", Integer.toString(value.query().omittedEvidenceCount()));
        metadata(result, value, "truncated", Boolean.toString(value.query().truncated()));
        metadata(result, value, "maxNodes", Integer.toString(value.query().limits().maxNodes()));
        metadata(result, value, "maxEdges", Integer.toString(value.query().limits().maxEdges()));
        metadata(result, value, "maxEvidencePerEdge",
                Integer.toString(value.query().limits().maxEvidencePerEdge()));
        for (SnapshotNode node : value.nodes()) {
            row(result, fields(
                    value.schemaVersion(), "node", node.id(), "", "", node.label(), "",
                    Integer.toString(node.sourceNodeCount()), "", "", "", "", "", "", "", "", "", ""));
            for (String sourceId : node.sourceNodeIds()) {
                row(result, fields(
                        value.schemaVersion(), "node-source", sourceId, node.id(), "", "", "",
                        "", "", "", "", "", "", "", "", "", "", ""));
            }
        }
        for (SnapshotEdge edge : value.edges()) {
            row(result, fields(
                    value.schemaVersion(), "edge", edge.id(), edge.originId(), edge.targetId(), "",
                    String.join("|", edge.dependencyKinds()), "", Integer.toString(edge.sourceEdgeCount()),
                    Integer.toString(edge.evidenceCount()), Integer.toString(edge.omittedEvidenceCount()),
                    "", "", "", "", "", "", ""));
            for (String sourceEdge : edge.sourceEdges()) {
                row(result, fields(
                        value.schemaVersion(), "edge-source", sourceEdge, edge.id(), "", "", "",
                        "", "", "", "", "", "", "", "", "", "", ""));
            }
            for (int index = 0; index < edge.evidence().size(); index++) {
                SnapshotEvidence evidence = edge.evidence().get(index);
                row(result, fields(
                        value.schemaVersion(),
                        "evidence",
                        edge.id() + "/evidence/"
                                + String.format(Locale.ROOT, "%06d", index + 1),
                        edge.id(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        evidence.locationId(),
                        evidence.ownerMemberId().orElse(""),
                        evidence.bytecodeOffset().isPresent()
                                ? Integer.toString(evidence.bytecodeOffset().getAsInt()) : "",
                        evidence.sourceFile().orElse(""),
                        evidence.lineNumber().isPresent()
                                ? Integer.toString(evidence.lineNumber().getAsInt()) : "",
                        "",
                        ""));
            }
        }
        return result.toString();
    }

    public static byte[] renderBytes(GraphSnapshot snapshot) {
        return render(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the lossless machine-readable representation as UTF-8 bytes. */
    public static byte[] renderMachineReadableBytes(GraphSnapshot snapshot) {
        return renderMachineReadable(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    private static void metadata(
            StringBuilder out, GraphSnapshot snapshot, String key, String value) {
        row(out, fields(
                snapshot.schemaVersion(), "metadata", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", key, value));
    }

    private static void metadataList(
            StringBuilder out, GraphSnapshot snapshot, String key, List<String> values) {
        metadata(out, snapshot, key + "Count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            metadata(
                    out,
                    snapshot,
                    key + "." + String.format(Locale.ROOT, "%06d", index + 1),
                    values.get(index));
        }
    }

    private static List<String> fields(String... values) {
        return List.of(values.clone());
    }

    private static void row(StringBuilder out, List<String> values) {
        if (values.size() != HEADER.size()) {
            throw new IllegalArgumentException("CSV row does not match schema");
        }
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            out.append('"').append(values.get(index).replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
    }

    private static String neutralizeFormulas(String csv) {
        StringBuilder result = new StringBuilder(csv.length());
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char character = csv.charAt(index);
            result.append(character);
            if (!quoted && character == '"') {
                quoted = true;
                if (index + 1 < csv.length() && formulaPrefix(csv.charAt(index + 1))) {
                    result.append('\'');
                }
            } else if (quoted && character == '"') {
                if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                    result.append('"');
                    index++;
                } else {
                    quoted = false;
                }
            }
        }
        return result.toString();
    }

    private static boolean formulaPrefix(char value) {
        return value == '=' || value == '+' || value == '-' || value == '@'
                || value == '\t' || value == '\r' || value == '\n';
    }
}
