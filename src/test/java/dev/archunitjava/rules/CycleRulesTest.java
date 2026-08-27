package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.PackageSelector;
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

class CycleRulesTest {
    private static final TypeId A = TypeId.ofBinaryName("app.A");
    private static final TypeId B = TypeId.ofBinaryName("app.B");
    private static final TypeId C = TypeId.ofBinaryName("app.C");
    private static final TypeId D = TypeId.ofBinaryName("other.D");
    private static final TypeId GENERATED = TypeId.ofBinaryName("app.Generated");
    private static final MemberId A_BRIDGE = MemberId.of(A, "bridge", "()V");
    private static final MemberId B_WORK = MemberId.of(B, "work", "()V");
    private static final MemberId GENERATED_WORK = MemberId.of(GENERATED, "work", "()V");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importTypes() throws IOException {
        write("app/A.class", type("app.A", true));
        write("app/B.class", type("app.B", false));
        write("app/C.class", type("app.C", false));
        write("other/D.class", type("other.D", false));
        write("app/Generated.class", syntheticType("app.Generated"));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void oneStableRepresentativeIsReportedPerComponentWithBoundedEvidence() {
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(A).addNode(B).addNode(C).addNode(D)
                .addDependency(A, B, DependencyKind.METHOD_CALL, evidence(A_BRIDGE, 10))
                .addDependency(B, A, DependencyKind.METHOD_CALL, evidence(B_WORK, 20))
                .addDependency(A, C, DependencyKind.METHOD_CALL, evidence(A_BRIDGE, 30))
                .addDependency(C, A, DependencyKind.METHOD_CALL, evidence(null, 40))
                .build();
        CycleRuleOptions options = CycleRuleOptions.defaults()
                .withSyntheticEdges(SyntheticEdgePolicy.INCLUDE)
                .withBounds(8, 100, 1);
        ArchitectureRule rule = CycleRules.typesAreAcyclic(
                model, graph, packages("app"), options);

        var first = rule.check();
        var second = rule.check();

        assertEquals(first, second);
        assertEquals(1, first.violations().size());
        assertEquals("[type:app.A, type:app.B, type:app.A]",
                first.violations().getFirst().attributes().get("representativeCycle"));
        assertEquals("3", first.violations().getFirst().attributes().get("componentSize"));
        assertEquals("true", first.violations().getFirst().attributes().get("evidenceTruncated"));
        assertEquals(1, first.violations().getFirst().evidence().size());
        assertTrue(first.diagnostics().stream()
                .anyMatch(value -> value.code().equals("cycle.representative.truncated")));
    }

    @Test
    void selectorExclusionsRemoveNodesAndEveryIncidentEdge() {
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(A).addNode(B).addNode(C).addNode(D)
                .addDependency(A, B, DependencyKind.METHOD_CALL, evidence(null, 10))
                .addDependency(B, C, DependencyKind.METHOD_CALL, evidence(null, 20))
                .addDependency(C, A, DependencyKind.METHOD_CALL, evidence(null, 30))
                .build();
        TypeSelector withoutC = packages("app").excluding(binary("app.C"));

        assertEquals(RuleStatus.FAILED,
                CycleRules.typesAreAcyclic(model, graph, packages("app")).check().status());
        assertEquals(RuleStatus.PASSED,
                CycleRules.typesAreAcyclic(model, graph, withoutC).check().status());
    }

    @Test
    void typeUseAndSyntheticEdgesAreIndependentExplicitPolicies() {
        DependencyGraph typeUses = DependencyGraph.builder()
                .addNode(A).addNode(B)
                .addDependency(A, B, DependencyKind.FIELD_TYPE, evidence(null, 10))
                .addDependency(B, A, DependencyKind.FIELD_TYPE, evidence(null, 20))
                .build();
        CycleRuleOptions noTypeUses = CycleRuleOptions.defaults()
                .withTypeUseDependencies(TypeUseDependencyPolicy.IGNORE);
        assertEquals(RuleStatus.FAILED,
                CycleRules.typesAreAcyclic(model, typeUses, packages("app")).check().status());
        assertEquals(RuleStatus.PASSED,
                CycleRules.typesAreAcyclic(model, typeUses, packages("app"), noTypeUses)
                        .check().status());

        DependencyGraph synthetic = DependencyGraph.builder()
                .addNode(A).addNode(B)
                .addDependency(A, B, DependencyKind.METHOD_CALL, evidence(A_BRIDGE, 30))
                .addDependency(B, A, DependencyKind.METHOD_CALL, evidence(B_WORK, 40))
                .build();
        assertEquals(RuleStatus.PASSED,
                CycleRules.typesAreAcyclic(model, synthetic, packages("app")).check().status());
        assertEquals(RuleStatus.FAILED,
                CycleRules.typesAreAcyclic(
                        model,
                        synthetic,
                        packages("app"),
                        CycleRuleOptions.defaults().withSyntheticEdges(SyntheticEdgePolicy.INCLUDE))
                        .check().status());
    }

    @Test
    void packageProjectionIgnoresIntraPackageEdgesAndRetainsUnderlyingEvidence() {
        DependencyEvidence outward = evidence(null, 50);
        DependencyEvidence inward = evidence(null, 60);
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(A).addNode(B).addNode(D)
                .addDependency(A, B, DependencyKind.METHOD_CALL, evidence(null, 40))
                .addDependency(A, D, DependencyKind.METHOD_CALL, outward)
                .addDependency(D, B, DependencyKind.METHOD_CALL, inward)
                .build();

        var result = CycleRules.packagesAreAcyclic(
                model, graph, PackageSelector.all()).check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals("[package:app, package:other, package:app]",
                result.violations().getFirst().attributes().get("representativeCycle"));
        assertEquals(List.of(outward, inward), result.violations().getFirst().evidence());
    }

    @Test
    void ordinaryMemberOnSyntheticTypeStillProducesSyntheticEvidence() {
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(GENERATED).addNode(B)
                .addDependency(
                        GENERATED, B, DependencyKind.METHOD_CALL, evidence(GENERATED_WORK, 70))
                .addDependency(B, GENERATED, DependencyKind.METHOD_CALL, evidence(B_WORK, 80))
                .build();

        assertEquals(RuleStatus.PASSED,
                CycleRules.typesAreAcyclic(model, graph, packages("app")).check().status());
        assertEquals(RuleStatus.FAILED,
                CycleRules.typesAreAcyclic(
                        model,
                        graph,
                        packages("app"),
                        CycleRuleOptions.defaults().withSyntheticEdges(SyntheticEdgePolicy.INCLUDE))
                        .check().status());
    }

    private static byte[] type(String binaryName, boolean syntheticBridge) {
        return ClassFile.of().build(ClassDesc.of(binaryName), builder -> {
            builder.withMethodBody(
                    "work", MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                    ClassFile.ACC_PUBLIC, code -> code.return_());
            if (syntheticBridge) {
                builder.withMethodBody(
                        "bridge", MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE,
                        code -> code.return_());
            }
        });
    }

    private static byte[] syntheticType(String binaryName) {
        return ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder
                .withFlags(ClassFile.ACC_SYNTHETIC)
                .withMethodBody(
                        "work", MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                        ClassFile.ACC_PUBLIC, code -> code.return_()));
    }

    private static TypeSelector binary(String value) {
        return TypeSelector.binaryName(exact(value));
    }

    private static TypeSelector packages(String value) {
        return TypeSelector.packageName(exact(value));
    }

    private static JavaPattern exact(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private static DependencyEvidence evidence(MemberId owner, int line) {
        return new DependencyEvidence(
                LocationId.ofResourcePath("classes/Fixture.class"),
                java.util.Optional.ofNullable(owner),
                java.util.OptionalInt.empty(),
                java.util.Optional.of("Fixture.java"),
                java.util.OptionalInt.of(line));
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
