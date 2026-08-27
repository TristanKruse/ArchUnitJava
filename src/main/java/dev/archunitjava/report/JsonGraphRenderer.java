package dev.archunitjava.report;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Canonical JSON rendering of the versioned graph-snapshot schema. */
public final class JsonGraphRenderer {
    private JsonGraphRenderer() {}

    public static String render(GraphSnapshot snapshot) {
        GraphSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        StringBuilder result = new StringBuilder();
        result.append('{');
        field(result, "schemaVersion", value.schemaVersion()).append(',');
        result.append("\"query\":");
        query(result, value.query()).append(',');
        result.append("\"nodes\":[");
        for (int index = 0; index < value.nodes().size(); index++) {
            if (index > 0) result.append(',');
            node(result, value.nodes().get(index));
        }
        result.append("],\"edges\":[");
        for (int index = 0; index < value.edges().size(); index++) {
            if (index > 0) result.append(',');
            edge(result, value.edges().get(index));
        }
        return result.append("]}\n").toString();
    }

    public static byte[] renderBytes(GraphSnapshot snapshot) {
        return render(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    private static StringBuilder query(StringBuilder out, SnapshotQueryMetadata query) {
        out.append('{');
        field(out, "domain", query.domain().name()).append(',');
        strings(out, "includedKinds", query.includedKinds()).append(',');
        strings(out, "includedNodeIds", query.includedNodeIds()).append(',');
        strings(out, "excludedNodeIds", query.excludedNodeIds()).append(',');
        bool(out, "retainSelfEdges", query.retainSelfEdges()).append(',');
        number(out, "mappingCount", query.mappingCount()).append(',');
        number(out, "sourceNodeCount", query.sourceNodeCount()).append(',');
        number(out, "sourceEdgeCount", query.sourceEdgeCount()).append(',');
        number(out, "mappedSourceNodeCount", query.mappedSourceNodeCount()).append(',');
        number(out, "matchedNodeCount", query.matchedNodeCount()).append(',');
        number(out, "matchedEdgeCount", query.matchedEdgeCount()).append(',');
        number(out, "omittedNodeCount", query.omittedNodeCount()).append(',');
        number(out, "omittedEdgeCount", query.omittedEdgeCount()).append(',');
        number(out, "omittedEvidenceCount", query.omittedEvidenceCount()).append(',');
        bool(out, "truncated", query.truncated()).append(',');
        out.append("\"limits\":{");
        number(out, "maxNodes", query.limits().maxNodes()).append(',');
        number(out, "maxEdges", query.limits().maxEdges()).append(',');
        number(out, "maxEvidencePerEdge", query.limits().maxEvidencePerEdge());
        return out.append("}}");
    }

    private static void node(StringBuilder out, SnapshotNode node) {
        out.append('{');
        field(out, "id", node.id()).append(',');
        field(out, "label", node.label()).append(',');
        number(out, "sourceNodeCount", node.sourceNodeCount()).append(',');
        strings(out, "sourceNodeIds", node.sourceNodeIds());
        out.append('}');
    }

    private static void edge(StringBuilder out, SnapshotEdge edge) {
        out.append('{');
        field(out, "id", edge.id()).append(',');
        field(out, "originId", edge.originId()).append(',');
        field(out, "targetId", edge.targetId()).append(',');
        strings(out, "dependencyKinds", edge.dependencyKinds()).append(',');
        number(out, "sourceEdgeCount", edge.sourceEdgeCount()).append(',');
        strings(out, "sourceEdges", edge.sourceEdges()).append(',');
        number(out, "evidenceCount", edge.evidenceCount()).append(',');
        number(out, "omittedEvidenceCount", edge.omittedEvidenceCount()).append(',');
        out.append("\"evidence\":[");
        for (int index = 0; index < edge.evidence().size(); index++) {
            if (index > 0) out.append(',');
            evidence(out, edge.evidence().get(index));
        }
        out.append("]}");
    }

    private static void evidence(StringBuilder out, SnapshotEvidence evidence) {
        out.append('{');
        field(out, "locationId", evidence.locationId()).append(',');
        optional(out, "ownerMemberId", evidence.ownerMemberId()).append(',');
        optionalInt(out, "bytecodeOffset", evidence.bytecodeOffset()).append(',');
        optional(out, "sourceFile", evidence.sourceFile()).append(',');
        optionalInt(out, "lineNumber", evidence.lineNumber());
        out.append('}');
    }

    private static StringBuilder field(StringBuilder out, String name, String value) {
        return out.append('"').append(name).append("\":\"")
                .append(ReportEscapes.json(value)).append('"');
    }

    private static StringBuilder number(StringBuilder out, String name, int value) {
        return out.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder bool(StringBuilder out, String name, boolean value) {
        return out.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder strings(
            StringBuilder out, String name, Collection<String> values) {
        out.append('"').append(name).append("\":[");
        int index = 0;
        for (String value : values) {
            if (index++ > 0) out.append(',');
            out.append('"').append(ReportEscapes.json(value)).append('"');
        }
        return out.append(']');
    }

    private static StringBuilder optional(
            StringBuilder out, String name, Optional<String> value) {
        out.append('"').append(name).append("\":");
        return value.isPresent()
                ? out.append('"').append(ReportEscapes.json(value.orElseThrow())).append('"')
                : out.append("null");
    }

    private static StringBuilder optionalInt(
            StringBuilder out, String name, OptionalInt value) {
        out.append('"').append(name).append("\":");
        return value.isPresent() ? out.append(value.getAsInt()) : out.append("null");
    }
}
