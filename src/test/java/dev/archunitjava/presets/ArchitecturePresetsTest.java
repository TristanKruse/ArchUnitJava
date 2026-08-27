package dev.archunitjava.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitecturePresetsTest {
    private static final TypeId ENTITY = TypeId.ofBinaryName("project.core.Entity");
    private static final TypeId USE_CASE = TypeId.ofBinaryName("project.logic.UseCase");
    private static final TypeId ADAPTER = TypeId.ofBinaryName("project.boundary.Adapter");
    private static final TypeId FRAMEWORK = TypeId.ofBinaryName("project.runtime.Framework");
    private static final TypeId PLUGIN = TypeId.ofBinaryName("custom.plugins.Plugin");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importFixture() throws IOException {
        for (TypeId type : List.of(ENTITY, USE_CASE, ADAPTER, FRAMEWORK, PLUGIN)) {
            writeType(type.binaryName());
        }
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void cleanPresetUsesOnlyCallerSuppliedSelectorsAndEnforcesInwardDirection() {
        DependencyGraph graph = graph(
                edge(FRAMEWORK, ADAPTER, 10),
                edge(ADAPTER, USE_CASE, 20),
                edge(USE_CASE, ENTITY, 30),
                edge(ENTITY, FRAMEWORK, 40));
        ArchitecturePreset preset = clean(graph).excludingTypes(packages("custom.plugins"));

        assertEquals(List.of(
                "entities", "frameworks", "interface-adapters", "use-cases"),
                preset.layers().stream().map(PresetLayer::name).toList());
        assertTrue(result(preset.check(), "entities-dependencies").status() == RuleStatus.FAILED);
        assertTrue(result(preset.check(), "frameworks-dependencies").passed());
        assertTrue(result(preset.check(), "coverage").passed());
    }

    @Test
    void expandedLayersAndRulesCanBeRenamedExplainedExcludedAndExtended() {
        ArchitecturePreset composed = clean(graph())
                .renamedLayer("entities", "domain-model")
                .withRuleDisplayName("entities-dependencies", "Domain model stays independent")
                .becauseRule("entities-dependencies", "business policy must remain portable")
                .withoutRule("frameworks-dependencies")
                .withLayer(PresetLayer.named("plugins", packages("custom.plugins")))
                .withRule(PresetRule.mayOnlyAccess(
                        "plugin-dependencies", "plugins", List.of("domain-model")));

        assertTrue(composed.layers().stream().anyMatch(layer -> layer.name().equals("domain-model")));
        assertFalse(composed.layers().stream().anyMatch(layer -> layer.name().equals("entities")));
        assertTrue(composed.ruleDefinitions().stream()
                .noneMatch(rule -> rule.key().equals("frameworks-dependencies")));
        assertTrue(composed.ruleDefinitions().stream()
                .anyMatch(rule -> rule.key().equals("plugin-dependencies")));

        var decorated = rule(composed, "entities-dependencies");
        assertEquals("Domain model stays independent", decorated.metadata().displayName());
        assertEquals(Optional.of("business policy must remain portable"), decorated.metadata().rationale());
        assertTrue(result(composed.check(), "coverage").passed());
    }

    @Test
    void providerSdkMakesProviderFacingBoundariesExecutableAndInspectable() {
        TypeSelector consumerApi = packages("project.core");
        TypeSelector providerSpi = packages("project.logic");
        TypeSelector internals = packages("project.runtime");
        TypeSelector providerImplementations = packages("project.boundary");
        DependencyGraph graph = graph(
                edge(ADAPTER, USE_CASE, 10),
                edge(ADAPTER, FRAMEWORK, 20));
        ArchitecturePreset preset = ArchitecturePresets.providerSdk(
                        model,
                        graph,
                        consumerApi,
                        providerSpi,
                        internals,
                        providerImplementations)
                .excludingTypes(packages("custom.plugins"));

        assertEquals(PresetRuleMode.PUBLIC_INTERFACE, preset.ruleDefinitions().stream()
                .filter(rule -> rule.key().equals("provider-public-interface"))
                .findFirst().orElseThrow().mode());
        assertEquals(RuleStatus.FAILED,
                result(preset.check(), "provider-implementation-dependencies").status());
        assertEquals(RuleStatus.PASSED,
                result(preset.check(), "provider-public-interface").status());
    }

    @Test
    void onionAndHexagonalFactoriesAlsoRequireExplicitRoleSelectors() {
        ArchitecturePreset onion = ArchitecturePresets.onion(
                model,
                graph(),
                packages("project.core"),
                packages("project.logic"),
                packages("project.runtime"),
                packages("project.boundary"));
        ArchitecturePreset hexagonal = ArchitecturePresets.hexagonal(
                model,
                graph(),
                packages("project.core"),
                packages("project.logic"),
                packages("project.boundary"),
                packages("project.runtime"));

        assertEquals("onion", onion.name());
        assertEquals("hexagonal", hexagonal.name());
        assertEquals(5, onion.ruleDefinitions().size());
        assertEquals(5, hexagonal.ruleDefinitions().size());
    }

    private ArchitecturePreset clean(DependencyGraph graph) {
        return ArchitecturePresets.clean(
                model,
                graph,
                packages("project.core"),
                packages("project.logic"),
                packages("project.boundary"),
                packages("project.runtime"));
    }

    private static dev.archunitjava.rules.ArchitectureRule rule(
            ArchitecturePreset preset, String key) {
        String tag = "preset-rule:" + key;
        return preset.rules().stream()
                .filter(rule -> rule.metadata().tags().contains(tag))
                .findFirst()
                .orElseThrow();
    }

    private static RuleResult result(List<RuleResult> results, String key) {
        String tag = "preset-rule:" + key;
        return results.stream()
                .filter(result -> result.metadata().tags().contains(tag))
                .findFirst()
                .orElseThrow();
    }

    private static TypeSelector packages(String packageName) {
        return TypeSelector.packageName(
                JavaPattern.exact(PatternDomain.QUALIFIED_NAME, packageName));
    }

    private static Edge edge(TypeId origin, TypeId target, int line) {
        return new Edge(origin, target, line);
    }

    private static DependencyGraph graph(Edge... edges) {
        DependencyGraph.Builder builder = DependencyGraph.builder();
        List.of(ENTITY, USE_CASE, ADAPTER, FRAMEWORK, PLUGIN).forEach(builder::addNode);
        for (Edge edge : edges) {
            builder.addDependency(
                    edge.origin(),
                    edge.target(),
                    DependencyKind.METHOD_CALL,
                    new DependencyEvidence(
                            LocationId.ofResourcePath("classes/Fixture.class"),
                            Optional.empty(),
                            java.util.OptionalInt.empty(),
                            Optional.of("Fixture.java"),
                            java.util.OptionalInt.of(edge.line())));
        }
        return builder.build();
    }

    private void writeType(String binaryName) throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody(
                        "work",
                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                        ClassFile.ACC_PUBLIC,
                        code -> code.return_()));
        Path target = temporaryDirectory.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private record Edge(TypeId origin, TypeId target, int line) {}
}
