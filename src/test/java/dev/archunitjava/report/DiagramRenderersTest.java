package dev.archunitjava.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.LayerId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagramRenderersTest {
    private static final TypeId A = TypeId.ofBinaryName("app.A");
    private static final TypeId B = TypeId.ofBinaryName("app.B");
    private static final String HOSTILE = "bad\"]; URL=\"https://evil.invalid\" <TABLE> %%{init} click";

    @Test
    void dotUsesGeneratedIdentifiersAndQuotedEscapedAttributes() {
        GraphSnapshot snapshot = snapshot(mapping(false), graph(false));

        String dot = DotGraphRenderer.render(snapshot);

        assertTrue(dot.contains("digraph architecture {\n"));
        assertTrue(dot.contains("n000001"));
        assertTrue(dot.contains("stable_id=\"layer:"));
        assertTrue(dot.contains("bad\\\"]"));
        assertFalse(dot.contains("label=\"bad\"]"));
        assertFalse(dot.contains("\r"));
    }

    @Test
    void mermaidEncodesSyntaxHtmlAndDirectiveCharactersInsideLabels() {
        GraphSnapshot snapshot = snapshot(mapping(false), graph(false));

        String mermaid = MermaidGraphRenderer.render(snapshot);

        assertTrue(mermaid.startsWith("%% schema: archunitjava.graph-snapshot.v1\n"));
        assertTrue(mermaid.contains("&quot;"));
        assertTrue(mermaid.contains("&lt;TABLE&gt;"));
        assertTrue(mermaid.contains("&#37;&#37;&#123;init&#125;"));
        assertFalse(mermaid.contains("<TABLE>"));
        assertFalse(mermaid.lines().anyMatch(line -> line.stripLeading().startsWith("click ")));
        assertFalse(mermaid.lines().anyMatch(line -> line.stripLeading().startsWith("%%{")));
        assertFalse(mermaid.contains("\r"));
    }

    @Test
    void equivalentSnapshotsProduceByteIdenticalOutput() {
        GraphSnapshot first = snapshot(mapping(false), graph(false));
        GraphSnapshot second = snapshot(mapping(true), graph(true));

        assertEquals(first, second);
        assertArrayEquals(DotGraphRenderer.renderBytes(first), DotGraphRenderer.renderBytes(second));
        assertArrayEquals(
                MermaidGraphRenderer.renderBytes(first), MermaidGraphRenderer.renderBytes(second));
    }

    @Test
    void aggregationAndTruncationAreVisibleInBothFormats() {
        GraphSnapshot snapshot = snapshot(mapping(false), graph(false));
        String dot = DotGraphRenderer.render(snapshot);
        String mermaid = MermaidGraphRenderer.render(snapshot);

        assertTrue(snapshot.query().truncated());
        assertTrue(dot.contains("snapshot_truncated=\"true\""));
        assertTrue(dot.contains("evidence_count=\"2\""));
        assertTrue(dot.contains("omitted_evidence=\"1\""));
        assertTrue(mermaid.contains("%% truncated: true"));
        assertTrue(mermaid.contains("evidence: 2; omitted evidence: 1"));
    }

    private static GraphSnapshot snapshot(
            Map<TypeId, LayerId> mappings, DependencyGraph graph) {
        return GraphSnapshotQuery.layers(graph, mappings)
                .limitedBy(new GraphSnapshotLimits(2, 1, 1))
                .snapshot();
    }

    private static Map<TypeId, LayerId> mapping(boolean reverse) {
        LinkedHashMap<TypeId, LayerId> result = new LinkedHashMap<>();
        if (reverse) {
            result.put(B, LayerId.named("safe"));
            result.put(A, LayerId.named(HOSTILE));
        } else {
            result.put(A, LayerId.named(HOSTILE));
            result.put(B, LayerId.named("safe"));
        }
        return result;
    }

    private static DependencyGraph graph(boolean reverseEvidence) {
        DependencyEvidence first = DependencyEvidence.at(LocationId.ofResourcePath("classes/A.class"));
        DependencyEvidence second = DependencyEvidence.at(LocationId.ofResourcePath("classes/B.class"));
        DependencyGraph.Builder result = DependencyGraph.builder().addNode(A).addNode(B);
        List<DependencyEvidence> evidence = reverseEvidence
                ? List.of(second, first) : List.of(first, second);
        evidence.forEach(item -> result.addDependency(
                A, B, DependencyKind.METHOD_CALL, item));
        return result.build();
    }
}
