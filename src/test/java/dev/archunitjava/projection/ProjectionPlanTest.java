package dev.archunitjava.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.ModuleId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.TypeId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectionPlanTest {
    private static final TypeId A = TypeId.ofBinaryName("a.A");
    private static final TypeId B = TypeId.ofBinaryName("a.B");
    private static final TypeId C = TypeId.ofBinaryName("b.C");
    private static final TypeId ISOLATED = TypeId.ofBinaryName("c.Isolated");
    private static final DependencyEvidence E1 = evidence("classes/a/A.class");
    private static final DependencyEvidence E2 = evidence("classes/a/B.class");
    private static final DependencyEvidence E3 = evidence("classes/b/C.class");
    private static final DependencyEvidence E4 = evidence("classes/meta/Annotation.class");

    @Test
    void packageProjectionFiltersRelabelsDropsSelfEdgesAndRetainsIsolatedNodes() {
        DependencyGraph source = typeGraph();
        ProjectionPlan plan = ProjectionPlan.packages()
                .includingOnly(Set.of(DependencyKind.FIELD_TYPE, DependencyKind.METHOD_CALL))
                .relabeling(DependencyKind.FIELD_TYPE, DependencyKind.TYPE_REFERENCE)
                .relabeling(DependencyKind.METHOD_CALL, DependencyKind.TYPE_REFERENCE)
                .withoutSelfEdges();

        ProjectionResult first = plan.apply(source);
        ProjectionResult second = plan.apply(source);

        assertEquals(first, second);
        assertEquals(List.of(PackageId.named("a"), PackageId.named("b"), PackageId.named("c")),
                first.graph().nodes().stream().map(node -> (PackageId) node.id()).toList());
        assertEquals(1, first.graph().edges().size());
        var edge = first.graph().edges().getFirst();
        assertEquals(PackageId.named("a"), edge.origin());
        assertEquals(PackageId.named("b"), edge.target());
        assertEquals(DependencyKind.TYPE_REFERENCE, edge.kind());
        assertEquals(List.of(E1, E2), edge.evidence());
        assertEquals(
                List.of(ProjectionDropReason.SELF_EDGE, ProjectionDropReason.FILTERED_KIND),
                first.droppedEdges().stream().map(DroppedProjectionEdge::reason).toList());
        assertEquals(4, source.nodes().size());
        assertEquals(4, source.edges().size());
    }

    @Test
    void memberAndTypeViewsUseTheSameContractAndAggregateParallelEvidence() {
        MemberId first = MemberId.of(A, "first", "()V");
        MemberId second = MemberId.of(A, "second", "()V");
        MemberId target = MemberId.of(C, "target", "()V");
        MemberId isolated = MemberId.of(ISOLATED, "unused", "()V");
        DependencyGraph source = DependencyGraph.builder()
                .addNode(first).addNode(second).addNode(target).addNode(isolated)
                .addDependency(first, target, DependencyKind.METHOD_CALL, E1)
                .addDependency(second, target, DependencyKind.METHOD_CALL, E2)
                .build();

        ProjectionResult members = ProjectionPlan.members().apply(source);
        ProjectionResult types = ProjectionPlan.types().apply(source);

        assertEquals(4, members.graph().nodes().size());
        assertEquals(2, members.graph().edges().size());
        assertEquals(List.of(A, C, ISOLATED),
                types.graph().nodes().stream().map(node -> (TypeId) node.id()).toList());
        assertEquals(1, types.graph().edges().size());
        assertEquals(List.of(E1, E2), types.graph().edges().getFirst().evidence());
        assertTrue(types.droppedEdges().isEmpty());
    }

    @Test
    void classpathAndModuleViewsUseExplicitImmutableMappings() {
        DependencyGraph source = typeGraph();
        LocationId mainClasses = LocationId.ofResourcePath("classpath/main");
        LocationId dependencyJar = LocationId.ofResourcePath("classpath/dependency.jar");
        ProjectionPlan classpath = ProjectionPlan.classPath(Map.of(
                A, mainClasses, B, mainClasses, C, dependencyJar, ISOLATED, mainClasses));
        ModuleId application = ModuleId.named("application.module");
        ModuleId dependency = ModuleId.named("dependency.module");
        ProjectionPlan modules = ProjectionPlan.modules(Map.of(
                A, application, B, application, C, dependency, ISOLATED, application));

        ProjectionResult classpathResult = classpath.apply(source);
        ProjectionResult moduleResult = modules.apply(source);

        assertEquals(List.of(mainClasses, dependencyJar).stream().sorted().toList(),
                classpathResult.graph().nodes().stream().map(node -> (LocationId) node.id()).toList());
        assertEquals(List.of(application, dependency).stream().sorted().toList(),
                moduleResult.graph().nodes().stream().map(node -> (ModuleId) node.id()).toList());
        assertEquals(source.edges().stream().mapToInt(edge -> edge.evidence().size()).sum(),
                classpathResult.graph().edges().stream().mapToInt(edge -> edge.evidence().size()).sum());
        assertThrows(UnsupportedOperationException.class, () -> classpath.explicitMappings().clear());
    }

    @Test
    void exactEdgeExclusionsAreValuesAndUnmappedEndpointsAreAccountedFor() {
        DependencyGraph source = typeGraph();
        ProjectionPlan excluded = ProjectionPlan.types()
                .excluding(A, C, DependencyKind.FIELD_TYPE);
        ProjectionResult exclusionResult = excluded.apply(source);
        ProjectionResult unmapped = ProjectionPlan.modules(Map.of(A, ModuleId.named("only.a")))
                .apply(source);

        assertEquals(1, exclusionResult.droppedEdges().size());
        assertEquals(ProjectionDropReason.EXPLICITLY_EXCLUDED,
                exclusionResult.droppedEdges().getFirst().reason());
        assertTrue(unmapped.droppedEdges().stream()
                .allMatch(value -> value.reason() == ProjectionDropReason.UNMAPPED_ENDPOINT));
        assertEquals(1, unmapped.graph().nodes().size());
    }

    private static DependencyGraph typeGraph() {
        return DependencyGraph.builder()
                .addNode(A).addNode(B).addNode(C).addNode(ISOLATED)
                .addDependency(A, B, DependencyKind.FIELD_TYPE, E3)
                .addDependency(A, C, DependencyKind.FIELD_TYPE, E1)
                .addDependency(B, C, DependencyKind.METHOD_CALL, E2)
                .addDependency(A, C, DependencyKind.ANNOTATION, E4)
                .build();
    }

    private static DependencyEvidence evidence(String resource) {
        return DependencyEvidence.at(LocationId.ofResourcePath(resource));
    }
}
