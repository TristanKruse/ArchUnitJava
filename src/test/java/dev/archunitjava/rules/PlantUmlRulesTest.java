package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.diagram.plantuml.PlantUmlAdherenceOptions;
import dev.archunitjava.diagram.plantuml.PlantUmlDiagram;
import dev.archunitjava.diagram.plantuml.PlantUmlExporter;
import dev.archunitjava.diagram.plantuml.PlantUmlParser;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.layers.LayerDefinitions;
import dev.archunitjava.layers.LayerModel;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.TypeSelector;
import dev.archunitjava.slices.SliceDefinitions;
import dev.archunitjava.slices.SliceModel;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlantUmlRulesTest {
    private static final TypeId CORE = TypeId.ofBinaryName("app.core.Core");
    private static final TypeId SERVICE = TypeId.ofBinaryName("app.service.Service");
    private static final TypeId UI = TypeId.ofBinaryName("app.ui.Ui");
    private static final TypeId EXTERNAL = TypeId.ofBinaryName("external.Client");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;
    private LayerModel layers;
    private SliceModel slices;

    @BeforeEach
    void importFixture() throws IOException {
        writeType(CORE.binaryName());
        writeType(SERVICE.binaryName());
        writeType(UI.binaryName());
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
        layers = LayerDefinitions.builder()
                .required("Core", packages("app.core"))
                .required("Service", packages("app.service"))
                .required("UI", packages("app.ui"))
                .build(model);
        slices = SliceDefinitions.builder()
                .assign("Core", packages("app.core"))
                .assign("Service", packages("app.service"))
                .assign("UI", packages("app.ui"))
                .build(model);
    }

    @Test
    void missingAndForbiddenEdgesHaveJavaEvidence() {
        DependencyEvidence allowed = evidence(10);
        DependencyEvidence forbidden = evidence(20);
        DependencyGraph graph = graph(
                new Edge(UI, SERVICE, allowed),
                new Edge(CORE, UI, forbidden));
        PlantUmlDiagram diagram = PlantUmlParser.parse("""
                @startuml
                component "Core" as core <<layer>>
                component "Service" as service <<layer>>
                component "UI" as ui <<layer>>
                ui --> service
                service ..> core
                @enduml
                """, Set.of("layer"));

        var result = PlantUmlRules.layers(model, layers, graph, diagram).check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(List.of("plantuml.edge.forbidden", "plantuml.edge.missing"),
                result.violations().stream().map(value -> value.code()).distinct().sorted().toList());
        assertEquals(List.of(forbidden), result.violations().stream()
                .filter(value -> value.code().equals("plantuml.edge.forbidden"))
                .findFirst().orElseThrow().evidence());
        assertFalse(result.violations().stream()
                .filter(value -> value.code().equals("plantuml.edge.missing"))
                .findFirst().orElseThrow().evidence().isEmpty());
    }

    @Test
    void unmappedDependenciesAreOnlyIgnoredByExplicitPolicy() {
        DependencyGraph graph = graph(new Edge(UI, EXTERNAL, evidence(30)));
        PlantUmlDiagram diagram = PlantUmlParser.parse(
                "component \"UI\" as ui <<layer>>", Set.of("layer"));

        assertEquals(RuleStatus.FAILED,
                PlantUmlRules.layers(model, layers, graph, diagram).check().status());
        assertEquals(RuleStatus.PASSED,
                PlantUmlRules.layers(
                        model,
                        layers,
                        graph,
                        diagram,
                        PlantUmlAdherenceOptions.permissive()).check().status());
    }

    @Test
    void exportsAreEscapedByteStableAndRoundTripThroughTheSafeParser() {
        String unusualName = "Core \"API\"\\boundary";
        LayerModel unusualLayers = LayerDefinitions.builder()
                .required(unusualName, packages("app.core"))
                .required("Service", packages("app.service"))
                .required("UI", packages("app.ui"))
                .build(model);
        DependencyGraph first = graph(
                new Edge(UI, SERVICE, evidence(10)),
                new Edge(SERVICE, CORE, evidence(20)));
        DependencyGraph second = graph(
                new Edge(SERVICE, CORE, evidence(20)),
                new Edge(UI, SERVICE, evidence(10)));

        byte[] firstExport = PlantUmlExporter.layersBytes(unusualLayers, first);
        byte[] secondExport = PlantUmlExporter.layersBytes(unusualLayers, second);
        assertArrayEquals(firstExport, secondExport);
        String text = new String(firstExport, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("Core \\\"API\\\"\\\\boundary"));
        PlantUmlDiagram parsed = PlantUmlParser.parse(text, Set.of("layer"));
        assertTrue(parsed.components().stream()
                .anyMatch(component -> component.displayName().equals(unusualName)));
        assertEquals(RuleStatus.PASSED,
                PlantUmlRules.layers(model, unusualLayers, first, parsed).check().status());
    }

    @Test
    void sliceExportsUseTheSameBoundedAdherencePath() {
        DependencyGraph graph = graph(
                new Edge(UI, SERVICE, evidence(10)),
                new Edge(SERVICE, CORE, evidence(20)));
        String exported = PlantUmlExporter.slices(slices, graph);
        PlantUmlDiagram parsed = PlantUmlParser.parse(exported, Set.of("slice"));

        assertTrue(exported.contains("<<slice>>"));
        assertEquals(RuleStatus.PASSED,
                PlantUmlRules.slices(model, slices, graph, parsed).check().status());
    }

    private static TypeSelector packages(String name) {
        return TypeSelector.packageName(
                JavaPattern.exact(PatternDomain.QUALIFIED_NAME, name));
    }

    private static DependencyGraph graph(Edge... edges) {
        DependencyGraph.Builder result = DependencyGraph.builder();
        List.of(CORE, SERVICE, UI, EXTERNAL).forEach(result::addNode);
        for (Edge edge : edges) {
            result.addDependency(
                    edge.origin(), edge.target(), DependencyKind.METHOD_CALL, edge.evidence());
        }
        return result.build();
    }

    private static DependencyEvidence evidence(int line) {
        return new DependencyEvidence(
                LocationId.ofResourcePath("classes/Fixture.class"),
                Optional.empty(),
                java.util.OptionalInt.empty(),
                Optional.of("Fixture.java"),
                java.util.OptionalInt.of(line));
    }

    private void writeType(String binaryName) throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(binaryName), builder -> {});
        Path target = temporaryDirectory.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private record Edge(TypeId origin, TypeId target, DependencyEvidence evidence) {}
}
