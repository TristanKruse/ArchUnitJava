package dev.archunitjava.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.ModuleId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.LayerId;
import dev.archunitjava.slices.SliceId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphSnapshotQueryTest {
    private static final TypeId A = TypeId.ofBinaryName("app.alpha.A");
    private static final TypeId B = TypeId.ofBinaryName("app.beta.B");
    private static final MemberId A_WORK = MemberId.of(A, "work", "()V");
    private static final MemberId B_WORK = MemberId.of(B, "work", "()V");
    private DependencyGraph graph;

    @BeforeEach
    void createGraph() {
        DependencyEvidence first = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/A.class"), A_WORK, 1, "A.java", 10);
        DependencyEvidence second = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/A.class"), A_WORK, 2, "A.java", 11);
        DependencyEvidence third = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/A.class"));
        graph = DependencyGraph.builder()
                .addNode(A).addNode(B).addNode(A_WORK).addNode(B_WORK)
                .addDependency(A_WORK, B_WORK, DependencyKind.METHOD_CALL, first)
                .addDependency(A_WORK, B_WORK, DependencyKind.METHOD_CALL, second)
                .addDependency(A_WORK, B_WORK, DependencyKind.FIELD_TYPE, third)
                .addDependency(A, B, DependencyKind.TYPE_REFERENCE, third)
                .build();
    }

    @Test
    void builtInPackageTypeAndMemberCollapseRetainCountsAndDrillDown() {
        GraphSnapshot packages = GraphSnapshotQuery.packages(graph).snapshot();
        GraphSnapshot types = GraphSnapshotQuery.types(graph).snapshot();
        GraphSnapshot members = GraphSnapshotQuery.members(graph).snapshot();

        assertEquals(ReportDomain.PACKAGE, packages.query().domain());
        assertEquals(2, packages.nodes().size());
        assertEquals(2, packages.nodes().getFirst().sourceNodeCount());
        assertEquals(3, packages.edges().getFirst().sourceEdgeCount());
        assertEquals(3, packages.edges().getFirst().evidenceCount());
        assertEquals(2, types.nodes().size());
        assertEquals(3, types.edges().getFirst().sourceEdgeCount());
        assertEquals(2, members.nodes().size());
        assertEquals(2, members.edges().getFirst().sourceEdgeCount());
        assertEquals(List.of("FIELD_TYPE", "METHOD_CALL"),
                members.edges().getFirst().dependencyKinds());
    }

    @Test
    void layerSliceArtifactAndModuleMappingsCollapseMembersThroughTheirOwners() {
        GraphSnapshot layers = GraphSnapshotQuery.layers(graph, Map.of(
                A, LayerId.named("core"), B, LayerId.named("adapter"))).snapshot();
        GraphSnapshot slices = GraphSnapshotQuery.slices(graph, Map.of(
                A, SliceId.named("alpha"), B, SliceId.named("beta"))).snapshot();
        GraphSnapshot artifacts = GraphSnapshotQuery.artifacts(graph, Map.of(
                A, LocationId.ofResourcePath("artifacts/a.jar"),
                B, LocationId.ofResourcePath("artifacts/b.jar"))).snapshot();
        GraphSnapshot modules = GraphSnapshotQuery.modules(graph, Map.of(
                A, ModuleId.named("app.alpha"), B, ModuleId.named("app.beta"))).snapshot();

        for (GraphSnapshot snapshot : List.of(layers, slices, artifacts, modules)) {
            assertEquals(2, snapshot.nodes().size());
            assertEquals(1, snapshot.edges().size());
            assertEquals(3, snapshot.edges().getFirst().sourceEdgeCount());
        }
        assertEquals("layer:adapter", layers.nodes().getFirst().id());
        assertEquals("slice:alpha", slices.nodes().getFirst().id());
        assertEquals("location:artifacts/a.jar", artifacts.nodes().getFirst().id());
        assertEquals("module:app.alpha", modules.nodes().getFirst().id());
    }

    @Test
    void snapshotsAreDetachedImmutableAndDeterministic() {
        Map<StableId, LayerId> mutableMappings = new HashMap<>();
        mutableMappings.put(A, LayerId.named("core"));
        mutableMappings.put(B, LayerId.named("adapter"));
        GraphSnapshotQuery query = GraphSnapshotQuery.layers(graph, mutableMappings);
        mutableMappings.put(A, LayerId.named("changed-after-query"));

        GraphSnapshot first = query.snapshot();
        GraphSnapshot second = query.snapshot();

        assertEquals(first, second);
        assertTrue(first.nodes().stream().noneMatch(node -> node.label().contains("changed")));
        assertThrows(UnsupportedOperationException.class, () -> first.nodes().add(first.nodes().getFirst()));
        assertThrows(UnsupportedOperationException.class, () ->
                first.edges().getFirst().evidence().clear());
        assertTrue(first.edges().getFirst().id().startsWith("report-edge:"));
    }

    @Test
    void filteringSelfEdgesAndLimitsAreExplicitInMetadata() {
        GraphSnapshot filtered = GraphSnapshotQuery.types(graph)
                .includingKinds(DependencyKind.METHOD_CALL)
                .snapshot();
        assertEquals(1, filtered.edges().getFirst().sourceEdgeCount());
        assertEquals(List.of("METHOD_CALL"), filtered.query().includedKinds());

        GraphSnapshot limited = GraphSnapshotQuery.types(graph)
                .limitedBy(new GraphSnapshotLimits(2, 1, 1))
                .snapshot();
        assertEquals(1, limited.edges().getFirst().evidence().size());
        assertEquals(2, limited.edges().getFirst().omittedEvidenceCount());
        assertEquals(2, limited.query().omittedEvidenceCount());
        assertTrue(limited.query().truncated());

        GraphSnapshot collapsed = GraphSnapshotQuery.layers(graph, Map.of(
                        A, LayerId.named("one"), B, LayerId.named("one")))
                .snapshot();
        GraphSnapshot retained = GraphSnapshotQuery.layers(graph, Map.of(
                        A, LayerId.named("one"), B, LayerId.named("one")))
                .retainingSelfEdges(true)
                .snapshot();
        assertTrue(collapsed.edges().isEmpty());
        assertEquals(1, retained.edges().size());
    }

    @Test
    void includedAndExcludedFiltersAddressCollapsedStableIds() {
        LayerId core = LayerId.named("core");
        LayerId adapter = LayerId.named("adapter");
        GraphSnapshot onlyCore = GraphSnapshotQuery.layers(graph, Map.of(A, core, B, adapter))
                .includingNodes(List.of(core))
                .snapshot();
        GraphSnapshot withoutAdapter = GraphSnapshotQuery.layers(graph, Map.of(A, core, B, adapter))
                .excludingNodes(List.of(adapter))
                .snapshot();

        assertEquals(List.of("layer:core"), onlyCore.nodes().stream().map(SnapshotNode::id).toList());
        assertEquals(onlyCore.nodes(), withoutAdapter.nodes());
        assertEquals(onlyCore.edges(), withoutAdapter.edges());
        assertEquals(List.of("layer:core"), onlyCore.query().includedNodeIds());
        assertEquals(List.of("layer:adapter"), withoutAdapter.query().excludedNodeIds());
        assertThrows(IllegalArgumentException.class, () ->
                GraphSnapshotQuery.layers(graph, Map.of(A, core, B, adapter))
                        .includingNodes(List.of(core))
                        .excludingNodes(List.of(core)));
    }
}
