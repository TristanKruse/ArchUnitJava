package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.layers.LayerDefinitions;
import dev.archunitjava.layers.LayerId;
import dev.archunitjava.layers.LayerMembershipException;
import dev.archunitjava.layers.LayerModel;
import dev.archunitjava.layers.LayerOverlapPolicy;
import dev.archunitjava.layers.LayerPresence;
import dev.archunitjava.layers.LayerSelector;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LayerRulesTest {
    private static final TypeId UI = TypeId.ofBinaryName("app.ui.Controller");
    private static final TypeId SERVICE = TypeId.ofBinaryName("app.service.Service");
    private static final TypeId REPOSITORY = TypeId.ofBinaryName("app.persistence.Repository");
    private static final TypeId EXTERNAL = TypeId.ofBinaryName("external.Client");
    private static final MemberId UI_WORK = MemberId.of(UI, "work", "()V");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importTypes() throws IOException {
        writeType(UI.binaryName());
        writeType(SERVICE.binaryName());
        writeType(REPOSITORY.binaryName());
        writeType(EXTERNAL.binaryName());
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void requiredAndOptionalLayersHaveExplicitEmptyBehavior() {
        LayerModel layers = definitions().build(model);

        assertEquals(List.of("adapter", "persistence", "service", "ui"), layers.layers().stream()
                .map(layer -> layer.id().name()).toList());
        assertEquals(LayerPresence.OPTIONAL, layers.layers().getFirst().presence());
        assertTrue(layers.layers().getFirst().types().isEmpty());
        assertThrows(LayerMembershipException.class, () -> LayerDefinitions.builder()
                .required("missing", binary("missing.Type"))
                .build(model));

        DependencyGraph emptyGraph = DependencyGraph.builder()
                .addNode(UI).addNode(SERVICE).addNode(REPOSITORY).addNode(EXTERNAL)
                .build();
        assertEquals(RuleStatus.PASSED, LayerRules.isolated(
                layers, emptyGraph, LayerSelector.named("adapter")).check().status());
        assertEquals(RuleStatus.INCOMPLETE, LayerRules.isolated(
                layers, emptyGraph, LayerSelector.named("missing")).check().status());
    }

    @Test
    void ambiguousMembershipFailsUnlessDeterministicPolicyIsExplicit() {
        var ambiguous = LayerDefinitions.builder()
                .optional("ui", packages("app.ui"))
                .optional("aaa", packages("app.ui"));

        assertThrows(LayerMembershipException.class, () -> ambiguous.build(model));

        LayerModel resolved = ambiguous
                .overlapPolicy(LayerOverlapPolicy.FIRST_BY_NAME)
                .build(model);
        assertEquals(LayerId.named("aaa"), resolved.layerOf(UI).orElseThrow());
        assertTrue(resolved.layers().stream()
                .filter(layer -> layer.id().equals(LayerId.named("ui")))
                .findFirst().orElseThrow().types().isEmpty());
    }

    @Test
    void mayOnlyAccessNoAccessAndOnlyAccessedByShareUnderlyingEvidence() {
        LayerModel layers = definitions().build(model);
        DependencyEvidence uiService = evidence(UI_WORK, 10);
        DependencyEvidence uiRepository = evidence(UI_WORK, 20);
        DependencyEvidence externalService = evidence(null, 30);
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(UI).addNode(SERVICE).addNode(REPOSITORY).addNode(EXTERNAL)
                .addDependency(UI, SERVICE, DependencyKind.METHOD_CALL, uiService)
                .addDependency(UI, REPOSITORY, DependencyKind.METHOD_CALL, uiRepository)
                .addDependency(EXTERNAL, SERVICE, DependencyKind.METHOD_CALL, externalService)
                .build();

        var mayOnly = LayerRules.mayOnlyAccess(
                layers,
                graph,
                LayerSelector.named("ui"),
                LayerSelector.named("service")).check();
        var forbidden = LayerRules.noAccess(
                layers,
                graph,
                LayerSelector.named("ui"),
                LayerSelector.named("persistence")).check();
        var inbound = LayerRules.onlyAccessedBy(
                layers,
                graph,
                LayerSelector.named("service"),
                LayerSelector.named("ui")).check();

        assertEquals(List.of(uiRepository), mayOnly.violations().getFirst().evidence());
        assertEquals(List.of(uiRepository), forbidden.violations().getFirst().evidence());
        assertEquals(List.of(externalService), inbound.violations().getFirst().evidence());
        assertEquals("layer:<unassigned>", inbound.violations().getFirst().subjects().stream()
                .filter(subject -> subject.role().equals("originLayer"))
                .findFirst().orElseThrow().id().stableKey());
        assertEquals("[member:app.ui.Controller#work()V]",
                mayOnly.violations().getFirst().attributes().get("underlyingMembers"));
        assertEquals(
                mayOnly.violations().getFirst().attributes().keySet(),
                forbidden.violations().getFirst().attributes().keySet());
        assertEquals(
                forbidden.violations().getFirst().attributes().keySet(),
                inbound.violations().getFirst().attributes().keySet());
    }

    @Test
    void isolatedAndIntentionallyEmptyAllowListsAreSupported() {
        LayerModel layers = definitions().build(model);
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(UI).addNode(SERVICE).addNode(REPOSITORY).addNode(EXTERNAL)
                .addDependency(SERVICE, REPOSITORY, DependencyKind.FIELD_TYPE, evidence(null, 40))
                .build();

        assertEquals(RuleStatus.FAILED, LayerRules.isolated(
                layers, graph, LayerSelector.named("persistence")).check().status());
        assertEquals(RuleStatus.FAILED, LayerRules.mayOnlyAccess(
                layers,
                graph,
                LayerSelector.named("service"),
                LayerSelector.none()).check().status());
    }

    private LayerDefinitions.Builder definitions() {
        return LayerDefinitions.builder()
                .required("ui", packages("app.ui"))
                .required("service", packages("app.service"))
                .required("persistence", packages("app.persistence"))
                .optional("adapter", packages("app.adapter"));
    }

    private static TypeSelector binary(String value) {
        return TypeSelector.binaryName(JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value));
    }

    private static TypeSelector packages(String value) {
        return TypeSelector.packageName(JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value));
    }

    private static DependencyEvidence evidence(MemberId owner, int line) {
        return new DependencyEvidence(
                LocationId.ofResourcePath("classes/Fixture.class"),
                java.util.Optional.ofNullable(owner),
                java.util.OptionalInt.empty(),
                java.util.Optional.of("Fixture.java"),
                java.util.OptionalInt.of(line));
    }

    private void writeType(String binaryName) throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder
                .withMethodBody(
                        "work",
                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                        ClassFile.ACC_PUBLIC,
                        code -> code.return_()));
        Path target = temporaryDirectory.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
