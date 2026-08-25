package dev.archunitjava.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CycleAlgorithmsTest {
    private static final TypeId A = TypeId.ofBinaryName("cycle.A");
    private static final TypeId B = TypeId.ofBinaryName("cycle.B");
    private static final TypeId C = TypeId.ofBinaryName("cycle.C");
    private static final TypeId D = TypeId.ofBinaryName("cycle.D");
    private static final TypeId ISOLATED = TypeId.ofBinaryName("cycle.Isolated");
    private static final DependencyEvidence EVIDENCE =
            DependencyEvidence.at(LocationId.ofResourcePath("classes/cycle.class"));

    @Test
    void findsStableComponentsSelfLoopsAndElementaryCycles() {
        CycleAnalysisResult result = CycleAlgorithms.analyze(graph(false));

        assertEquals(
                List.of(
                        new StronglyConnectedComponent(List.of(A, B, C), true),
                        new StronglyConnectedComponent(List.of(D), true),
                        new StronglyConnectedComponent(List.of(ISOLATED), false)),
                result.components());
        assertEquals(
                List.of(
                        new ElementaryCycle(List.of(A, B)),
                        new ElementaryCycle(List.of(A, B, C)),
                        new ElementaryCycle(List.of(D))),
                result.cycles());
        assertEquals(List.of(D, D), result.cycles().getLast().closedPath());
        assertFalse(result.enumerationTruncated());

        assertEquals(result, CycleAlgorithms.analyze(graph(true)));
    }

    @Test
    void collapsesParallelEdgeEvidenceForConnectivity() {
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(A).addNode(B)
                .addDependency(A, B, DependencyKind.FIELD_TYPE, EVIDENCE)
                .addDependency(A, B, DependencyKind.METHOD_CALL, EVIDENCE)
                .addDependency(B, A, DependencyKind.TYPE_REFERENCE, EVIDENCE)
                .build();

        CycleAnalysisResult result = CycleAlgorithms.analyze(graph);

        assertEquals(List.of(new ElementaryCycle(List.of(A, B))), result.cycles());
        assertEquals(1, result.cyclicComponents().size());
    }

    @Test
    void enumerationCanBeDisabledOrBoundedOnDenseGraphs() {
        DependencyGraph dense = denseGraph(7);

        CycleAnalysisResult componentsOnly = CycleAlgorithms.analyze(
                dense, CycleEnumerationOptions.componentsOnly());
        CycleAnalysisResult bounded = CycleAlgorithms.analyze(
                dense, CycleEnumerationOptions.bounded(2, 7, 100));

        assertEquals(1, componentsOnly.cyclicComponents().size());
        assertTrue(componentsOnly.cycles().isEmpty());
        assertFalse(componentsOnly.enumerationPerformed());
        assertEquals(2, bounded.cycles().size());
        assertTrue(bounded.enumerationTruncated());
        assertTrue(bounded.traversedEdges() <= 100);
    }

    private static DependencyGraph graph(boolean reversed) {
        List<TypeId> nodes = new ArrayList<>(List.of(A, B, C, D, ISOLATED));
        List<List<TypeId>> edges = new ArrayList<>(List.of(
                List.of(A, B), List.of(B, A), List.of(B, C), List.of(C, A), List.of(D, D)));
        if (reversed) {
            Collections.reverse(nodes);
            Collections.reverse(edges);
        }
        DependencyGraph.Builder builder = DependencyGraph.builder();
        nodes.forEach(builder::addNode);
        edges.forEach(edge -> builder.addDependency(
                edge.get(0), edge.get(1), DependencyKind.TYPE_REFERENCE, EVIDENCE));
        return builder.build();
    }

    private static DependencyGraph denseGraph(int size) {
        List<TypeId> nodes = java.util.stream.IntStream.range(0, size)
                .mapToObj(index -> TypeId.ofBinaryName("dense.N" + index))
                .toList();
        DependencyGraph.Builder builder = DependencyGraph.builder();
        nodes.forEach(builder::addNode);
        nodes.forEach(origin -> nodes.forEach(target -> builder.addDependency(
                origin, target, DependencyKind.TYPE_REFERENCE, EVIDENCE)));
        return builder.build();
    }
}
