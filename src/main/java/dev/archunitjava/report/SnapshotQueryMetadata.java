package dev.archunitjava.report;

import java.util.List;
import java.util.Objects;

/** Complete deterministic query and truncation metadata shared by every renderer. */
public record SnapshotQueryMetadata(
        ReportDomain domain,
        List<String> includedKinds,
        List<String> includedNodeIds,
        List<String> excludedNodeIds,
        boolean retainSelfEdges,
        int mappingCount,
        int sourceNodeCount,
        int sourceEdgeCount,
        int mappedSourceNodeCount,
        int matchedNodeCount,
        int matchedEdgeCount,
        int omittedNodeCount,
        int omittedEdgeCount,
        int omittedEvidenceCount,
        GraphSnapshotLimits limits) {
    public SnapshotQueryMetadata {
        Objects.requireNonNull(domain, "domain");
        includedKinds = strings(includedKinds, "includedKind");
        includedNodeIds = strings(includedNodeIds, "includedNodeId");
        excludedNodeIds = strings(excludedNodeIds, "excludedNodeId");
        nonNegative(mappingCount, "mappingCount");
        nonNegative(sourceNodeCount, "sourceNodeCount");
        nonNegative(sourceEdgeCount, "sourceEdgeCount");
        nonNegative(mappedSourceNodeCount, "mappedSourceNodeCount");
        nonNegative(matchedNodeCount, "matchedNodeCount");
        nonNegative(matchedEdgeCount, "matchedEdgeCount");
        nonNegative(omittedNodeCount, "omittedNodeCount");
        nonNegative(omittedEdgeCount, "omittedEdgeCount");
        nonNegative(omittedEvidenceCount, "omittedEvidenceCount");
        Objects.requireNonNull(limits, "limits");
        if (omittedNodeCount > matchedNodeCount || omittedEdgeCount > matchedEdgeCount) {
            throw new IllegalArgumentException("Omitted counts exceed matched counts");
        }
    }

    public boolean truncated() {
        return omittedNodeCount > 0 || omittedEdgeCount > 0 || omittedEvidenceCount > 0;
    }

    private static List<String> strings(List<String> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream()
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException(role + " must not be blank");
                    }
                    return value;
                })
                .distinct().sorted().toList();
    }

    private static void nonNegative(int value, String role) {
        if (value < 0) throw new IllegalArgumentException(role + " must not be negative");
    }
}
