package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.execution.EmptySelectionPolicy;
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
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyRulesTest {
    private static final TypeId A = TypeId.ofBinaryName("api.A");
    private static final TypeId B = TypeId.ofBinaryName("api.B");
    private static final TypeId C = TypeId.ofBinaryName("internal.C");
    private static final TypeId EXTERNAL = TypeId.ofBinaryName("external.Library");
    private static final DependencyEvidence AB = evidence("classes/api/A.class", 10);
    private static final DependencyEvidence AC = evidence("classes/api/A.class", 20);
    private static final DependencyEvidence SELF = evidence("classes/api/A.class", 30);
    private static final DependencyEvidence EXTERNAL_EVIDENCE =
            evidence("classes/internal/C.class", 40);

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;
    private DependencyGraph graph;

    @BeforeEach
    void buildFixture() throws IOException {
        write("api/A.class", ClassFile.of().build(ClassDesc.of("api.A"), builder -> {}));
        write("api/B.class", ClassFile.of().build(ClassDesc.of("api.B"), builder -> {}));
        write("internal/C.class", ClassFile.of().build(ClassDesc.of("internal.C"), builder -> {}));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
        graph = DependencyGraph.builder()
                .addNode(A).addNode(B).addNode(C).addNode(EXTERNAL)
                .addDependency(A, B, DependencyKind.METHOD_CALL, AB)
                .addDependency(A, C, DependencyKind.FIELD_TYPE, AC)
                .addDependency(A, A, DependencyKind.TYPE_REFERENCE, SELF)
                .addDependency(C, EXTERNAL, DependencyKind.METHOD_CALL, EXTERNAL_EVIDENCE)
                .build();
    }

    @Test
    void noOnlyAnyAndRequiredPoliciesShareEvidencePreservingResults() {
        TypeSelector a = binary("api.A");
        TypeSelector b = binary("api.B");
        TypeSelector c = binary("internal.C");
        TypeSelector api = packages("api");

        var forbidden = DependencyRules.types(
                model, graph, a, b,
                DependencyRuleSpec.noDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE)).check();
        var only = DependencyRules.types(
                model, graph, a, api,
                DependencyRuleSpec.onlyDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE)).check();
        var any = DependencyRules.types(
                model, graph, a, c,
                DependencyRuleSpec.anyDependency()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE)).check();
        var required = DependencyRules.types(
                model, graph, TypeSelector.anyOf(a, b), c,
                DependencyRuleSpec.requiredDependency()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE)).check();

        assertEquals(List.of(AB), forbidden.violations().getFirst().evidence());
        assertEquals(List.of(AC), only.violations().getFirst().evidence());
        assertEquals(RuleStatus.PASSED, any.status());
        assertEquals(RuleStatus.FAILED, required.status());
        assertEquals(1, required.violations().size());
        assertFalse(required.violations().getFirst().evidence().isEmpty());
        assertTrue(required.violations().getFirst().subjects().stream()
                .anyMatch(subject -> subject.id().equals(B)));
    }

    @Test
    void selfAndExternalPoliciesAreExplicit() {
        TypeSelector a = binary("api.A");
        TypeSelector c = binary("internal.C");
        DependencyRuleSpec ignoreSelf = DependencyRuleSpec.noDependencies()
                .withSelfDependencies(SelfDependencyPolicy.IGNORE)
                .withExternalDependencies(ExternalDependencyPolicy.IGNORE);
        DependencyRuleSpec includeSelf = ignoreSelf
                .withSelfDependencies(SelfDependencyPolicy.INCLUDE);

        assertEquals(RuleStatus.PASSED,
                DependencyRules.types(model, graph, a, a, ignoreSelf).check().status());
        assertEquals(List.of(SELF), DependencyRules.types(model, graph, a, a, includeSelf)
                .check().violations().getFirst().evidence());

        assertEquals(RuleStatus.PASSED, DependencyRules.types(
                model, graph, c, c,
                DependencyRuleSpec.onlyDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE)).check().status());
        assertEquals(RuleStatus.FAILED, DependencyRules.types(
                model, graph, c, c,
                DependencyRuleSpec.onlyDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.TREAT_AS_NON_MATCHING))
                .check().status());
        assertEquals(RuleStatus.INCOMPLETE, DependencyRules.types(
                model, graph, c, c,
                DependencyRuleSpec.onlyDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.FAIL)).check().status());
        assertEquals(RuleStatus.FAILED, DependencyRules.types(
                model, graph, c, c,
                DependencyRuleSpec.onlyDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.FAIL)).check(
                                CheckOptions.builder().allowIncompleteAnalysis(true).build()).status());
    }

    @Test
    void packageProjectionAndEmptyOriginPolicyUseTheSameTerminal() {
        var packageRule = DependencyRules.packages(
                model, graph,
                PackageSelector.name(exact("api")),
                PackageSelector.name(exact("internal")),
                DependencyRuleSpec.noDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE));
        var emptyRule = DependencyRules.types(
                model, graph,
                binary("missing.Type"),
                TypeSelector.all(),
                DependencyRuleSpec.noDependencies());
        var emptyTargetRule = DependencyRules.types(
                model, graph,
                binary("api.A"),
                binary("missing.Target"),
                DependencyRuleSpec.noDependencies());

        assertEquals(RuleStatus.FAILED, packageRule.check().status());
        assertEquals(List.of(AC), packageRule.check().violations().getFirst().evidence());
        assertEquals(RuleStatus.INCOMPLETE, emptyRule.check().status());
        assertEquals(RuleTerminal.EMPTY_SELECTION_CODE,
                emptyRule.check().diagnostics().getFirst().code());
        assertEquals(RuleStatus.INCOMPLETE, emptyTargetRule.check().status());
        assertEquals("targets", emptyTargetRule.check().diagnostics().getFirst().context().get("role"));
    }

    @Test
    void deliberatelyNonFailingEmptyOriginsNeverCrashAnyDependencyRules() {
        var rule = DependencyRules.types(
                model, graph,
                binary("missing.Type"),
                binary("api.B"),
                DependencyRuleSpec.anyDependency()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE));

        var warned = rule.check(CheckOptions.builder()
                .emptySelectionPolicy(EmptySelectionPolicy.WARN).build());
        var allowed = rule.check(CheckOptions.builder()
                .emptySelectionPolicy(EmptySelectionPolicy.ALLOW).build());

        assertEquals(RuleStatus.PASSED, warned.status());
        assertEquals(RuleTerminal.EMPTY_SELECTION_CODE, warned.diagnostics().getFirst().code());
        assertEquals(RuleStatus.PASSED, allowed.status());
        assertTrue(allowed.diagnostics().isEmpty());
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

    private static DependencyEvidence evidence(String path, int line) {
        return new DependencyEvidence(
                LocationId.ofResourcePath(path), java.util.Optional.empty(),
                java.util.OptionalInt.empty(), java.util.Optional.of("Fixture.java"),
                java.util.OptionalInt.of(line));
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
