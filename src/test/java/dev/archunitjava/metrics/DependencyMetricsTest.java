package dev.archunitjava.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.ModuleId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.projection.ProjectionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DependencyMetricsTest {
    private static final PackageId A = PackageId.named("a");
    private static final PackageId B = PackageId.named("b");
    private static final PackageId C = PackageId.named("c");
    private static final PackageId D = PackageId.named("d");

    @Test
    void canonicalLakosFixtureDefinesCcdAcdRacdAndNccd() {
        DependencyMetricReport report = analyze(packages(
                edge(A, B), edge(B, C), edge(A, D)));

        assertEquals(Map.of(A, 4, B, 2, C, 1, D, 1), report.cumulative().dependsOn());
        assertEquals(8, report.cumulative().cumulativeComponentDependency());
        assertEquals(2.0, report.cumulative().averageComponentDependency());
        assertEquals(0.5, report.cumulative().relativeAverageComponentDependency());
        assertEquals(1.0, report.cumulative().normalizedCumulativeComponentDependency());
    }

    @Test
    void martinMetricsCountDistinctComponentsAndDefineEmptyCases() {
        ProjectionResult graph = packages(edge(A, B), edge(B, C), edge(D, B));
        DependencyMetricReport report = new DependencyMetricAnalyzer().analyze(
                graph, List.of(new ComponentComposition(B, 3, 1)));
        ComponentDependencyMetrics b = component(report, B);

        assertEquals(2, b.afferentCoupling());
        assertEquals(1, b.efferentCoupling());
        assertEquals(1.0 / 3.0, b.instability());
        assertEquals(1.0 / 3.0, b.abstractness());
        assertEquals(1.0 / 3.0, b.distanceFromMainSequence(), 0.000000001);

        ProjectionResult isolated = packages(nodes(A));
        ComponentDependencyMetrics empty = component(analyze(isolated), A);
        assertEquals(0, empty.afferentCoupling());
        assertEquals(0, empty.efferentCoupling());
        assertEquals(0.0, empty.instability());
        assertEquals(0.0, empty.abstractness());
        assertEquals(1.0, empty.distanceFromMainSequence());

        DependencyMetricReport noSubjects = new DependencyMetricAnalyzer().analyze(
                isolated, List.<StableId>of(), List.of());
        assertEquals(List.of(), noSubjects.components());
        assertEquals(0, noSubjects.cumulative().cumulativeComponentDependency());
        assertEquals(0.0, noSubjects.cumulative().normalizedCumulativeComponentDependency());
    }

    @Test
    void subjectFiltersUseAnInducedGraphForCouplingAndReachability() {
        ProjectionResult graph = packages(edge(A, B), edge(B, C));
        DependencyMetricReport report = new DependencyMetricAnalyzer().analyze(
                graph, List.of(A, B), List.of());

        assertEquals(List.of(A, B), report.components().stream()
                .map(ComponentDependencyMetrics::component).toList());
        assertEquals(1, component(report, A).efferentCoupling());
        assertEquals(0, component(report, B).efferentCoupling());
        assertEquals(3, report.cumulative().cumulativeComponentDependency());
        assertThrows(IllegalArgumentException.class, () -> new DependencyMetricAnalyzer().analyze(
                graph, List.of(PackageId.named("missing")), List.of()));
    }

    @Test
    void cyclesAndDisconnectedComponentsHaveFiniteFormulaFixtures() {
        DependencyMetricReport report = analyze(packages(edge(A, B), edge(B, A), nodes(C)));

        assertEquals(Map.of(A, 2, B, 2, C, 1), report.cumulative().dependsOn());
        assertEquals(5, report.cumulative().cumulativeComponentDependency());
        assertEquals(5.0 / 3.0, report.cumulative().averageComponentDependency());
        assertEquals(5.0 / 9.0, report.cumulative().relativeAverageComponentDependency());
        assertEquals(1.0, report.cumulative().normalizedCumulativeComponentDependency());
    }

    @Test
    void splitPackagesMergeInPackageViewButRemainDistinctInModuleView() {
        TypeId one = TypeId.ofBinaryName("p.One");
        TypeId two = TypeId.ofBinaryName("p.Two");
        TypeId three = TypeId.ofBinaryName("q.Three");
        DependencyGraph raw = types(
                typeEdge(one, three, DependencyKind.TYPE_REFERENCE),
                typeEdge(one, three, DependencyKind.METHOD_CALL),
                typeEdge(two, three, DependencyKind.TYPE_REFERENCE));

        DependencyMetricReport packages = new DependencyMetricAnalyzer().analyze(
                ProjectionPlan.packages().withoutSelfEdges().apply(raw), List.of());
        assertEquals(1, component(packages, PackageId.named("p")).efferentCoupling());
        assertEquals(1, component(packages, PackageId.named("q")).afferentCoupling());

        ModuleId first = ModuleId.named("app.first");
        ModuleId second = ModuleId.named("app.second");
        ModuleId shared = ModuleId.named("app.shared");
        DependencyMetricReport modules = new DependencyMetricAnalyzer().analyze(
                ProjectionPlan.modules(Map.of(one, first, two, second, three, shared))
                        .withoutSelfEdges().apply(raw),
                List.of(
                        new ComponentComposition(first, 1, 0),
                        new ComponentComposition(second, 1, 0),
                        new ComponentComposition(shared, 1, 1)));
        assertEquals(1, component(modules, first).efferentCoupling());
        assertEquals(1, component(modules, second).efferentCoupling());
        assertEquals(2, component(modules, shared).afferentCoupling());
        assertEquals(1.0, component(modules, shared).abstractness());
    }

    private static DependencyMetricReport analyze(ProjectionResult graph) {
        return new DependencyMetricAnalyzer().analyze(graph, List.of());
    }

    private static ComponentDependencyMetrics component(
            DependencyMetricReport report, StableId subject) {
        return report.components().stream()
                .filter(value -> value.component().equals(subject))
                .findFirst().orElseThrow();
    }

    private static ProjectionResult packages(GraphPart... parts) {
        DependencyGraph.Builder graph = DependencyGraph.builder();
        for (GraphPart part : parts) part.addTo(graph);
        return ProjectionPlan.packages().withoutSelfEdges().apply(graph.build());
    }

    private static DependencyGraph types(GraphPart... parts) {
        DependencyGraph.Builder graph = DependencyGraph.builder();
        for (GraphPart part : parts) part.addTo(graph);
        return graph.build();
    }

    private static GraphPart edge(PackageId origin, PackageId target) {
        return graph -> graph.addNode(origin).addNode(target).addDependency(
                origin, target, DependencyKind.TYPE_REFERENCE, evidence(origin, target));
    }

    private static GraphPart typeEdge(TypeId origin, TypeId target, DependencyKind kind) {
        return graph -> graph.addNode(origin).addNode(target).addDependency(
                origin, target, kind, evidence(origin, target));
    }

    private static GraphPart nodes(StableId... subjects) {
        return graph -> java.util.Arrays.stream(subjects).forEach(graph::addNode);
    }

    private static DependencyEvidence evidence(StableId origin, StableId target) {
        String value = (origin.stableKey() + "-" + target.stableKey())
                .replace(':', '-').replace('.', '-');
        return DependencyEvidence.at(LocationId.ofResourcePath(value + ".class"));
    }

    @FunctionalInterface
    private interface GraphPart {
        void addTo(DependencyGraph.Builder graph);
    }
}
