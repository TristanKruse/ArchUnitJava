package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ReachabilityRulesTest {
    private static final TypeId ENTRY = type("api.Entry");
    private static final TypeId SERVICE = type("live.Service");
    private static final TypeId UTIL = type("live.Util");
    private static final TypeId EXTERNAL = type("external.Consumer");
    private static final TypeId EXTERNAL_DEPENDENCY = type("externaldep.Dependency");
    private static final TypeId REFLECTION = type("reflect.Loader");
    private static final TypeId REFLECTED = type("reflected.Target");
    private static final TypeId IGNORED = type("ignored.Bridge");
    private static final TypeId BEHIND_IGNORED = type("behind.Hidden");
    private static final TypeId DEAD_A = type("dead.A");
    private static final TypeId DEAD_B = type("dead.B");
    private static final TypeId ORPHAN = type("orphan.Leaf");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;
    private DependencyGraph graph;

    @BeforeEach
    void buildFixture() throws IOException {
        for (TypeId value : List.of(
                ENTRY, SERVICE, UTIL, EXTERNAL, EXTERNAL_DEPENDENCY, REFLECTION, REFLECTED,
                IGNORED, BEHIND_IGNORED, DEAD_A, DEAD_B, ORPHAN)) {
            boolean publiclyVisible = value.equals(ENTRY)
                    || value.equals(EXTERNAL)
                    || value.equals(REFLECTION);
            writeType(value.binaryName(), publiclyVisible);
        }
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
        graph = DependencyGraph.builder()
                .addNode(ENTRY).addNode(SERVICE).addNode(UTIL)
                .addNode(EXTERNAL).addNode(EXTERNAL_DEPENDENCY)
                .addNode(REFLECTION).addNode(REFLECTED)
                .addNode(IGNORED).addNode(BEHIND_IGNORED)
                .addNode(DEAD_A).addNode(DEAD_B)
                .addDependency(ENTRY, SERVICE, DependencyKind.METHOD_CALL, evidence(10))
                .addDependency(SERVICE, UTIL, DependencyKind.FIELD_TYPE, evidence(20))
                .addDependency(EXTERNAL, EXTERNAL_DEPENDENCY, DependencyKind.METHOD_CALL, evidence(30))
                .addDependency(REFLECTION, REFLECTED, DependencyKind.TYPE_REFERENCE, evidence(40))
                .addDependency(ENTRY, IGNORED, DependencyKind.METHOD_CALL, evidence(50))
                .addDependency(IGNORED, BEHIND_IGNORED, DependencyKind.METHOD_CALL, evidence(60))
                .addDependency(DEAD_A, DEAD_B, DependencyKind.METHOD_CALL, evidence(70))
                .addDependency(DEAD_B, DEAD_A, DependencyKind.FIELD_TYPE, evidence(80))
                .build();
    }

    @Test
    void explicitEntryKindsAndIgnoredSubjectsHaveDistinctSemantics() {
        ReachabilityRuleOptions options = ReachabilityRuleOptions.configuredEntryPoints()
                .withBounds(1, 1);
        var result = ReachabilityRules.unreachableTypes(
                model,
                graph,
                TypeSelector.all(),
                binary("api.Entry"),
                binary("external.Consumer"),
                binary("reflect.Loader"),
                binary("ignored.Bridge"),
                options).check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(3, result.violations().size());
        assertTrue(result.violations().stream().noneMatch(value -> value.subjects().stream()
                .anyMatch(subject -> subject.id().equals(IGNORED)
                        || subject.id().equals(EXTERNAL_DEPENDENCY)
                        || subject.id().equals(REFLECTED))));
        assertTrue(result.violations().stream().anyMatch(value -> value.subjects().stream()
                .anyMatch(subject -> subject.id().equals(BEHIND_IGNORED))));

        var cycle = result.violations().stream()
                .filter(value -> value.attributes().get("regionSize").equals("2"))
                .findFirst().orElseThrow();
        assertEquals("true", cycle.attributes().get("cyclicRegion"));
        assertEquals("true", cycle.attributes().get("subjectsTruncated"));
        assertEquals("true", cycle.attributes().get("evidenceTruncated"));
        assertEquals(1, cycle.subjects().size());
        assertEquals(1, cycle.evidence().size());
    }

    @Test
    void publicLibraryDefaultsAreConservativeAndDisclaimLiveness() {
        var result = ReachabilityRules.unreachablePublicLibraryTypes(
                model, graph, TypeSelector.all()).check();

        assertEquals(2, result.violations().size());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code().equals(ReachabilityRules.SCOPE_DIAGNOSTIC)
                        && value.context().get("wholeProgramLiveness").equals("NOT_CLAIMED")
                        && value.context().get("assumption")
                                .equals("PUBLIC_LIBRARY_CONSERVATIVE")));
        assertTrue(result.violations().stream().allMatch(value ->
                value.attributes().get("wholeProgramLiveness").equals("NOT_CLAIMED")));
    }

    @Test
    void packageReachabilityUsesTheSameExplicitInputsAndStableRegions() {
        var result = ReachabilityRules.unreachablePackages(
                model,
                graph,
                PackageSelector.all(),
                packageName("api"),
                packageName("external"),
                packageName("reflect"),
                packageName("ignored"),
                ReachabilityRuleOptions.configuredEntryPoints()).check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(List.of("package:behind", "package:dead", "package:orphan"),
                result.violations().stream()
                        .map(value -> value.subjects().getFirst().id().stableKey())
                        .sorted().toList());
        assertTrue(result.violations().stream().allMatch(value ->
                value.attributes().get("domain").equals("packages")));
    }

    @Test
    void absentEffectiveEntryPointsUseTheSharedEmptySelectionPolicy() {
        var result = ReachabilityRules.unreachableTypes(
                model,
                graph,
                TypeSelector.all(),
                TypeSelector.none(),
                TypeSelector.none(),
                TypeSelector.none(),
                TypeSelector.none(),
                ReachabilityRuleOptions.configuredEntryPoints()).check();

        assertEquals(RuleStatus.INCOMPLETE, result.status());
        assertEquals(RuleTerminal.EMPTY_SELECTION_CODE, result.diagnostics().getFirst().code());
        assertEquals("entryPoints", result.diagnostics().getFirst().context().get("role"));
    }

    private static TypeId type(String binaryName) {
        return TypeId.ofBinaryName(binaryName);
    }

    private static TypeSelector binary(String name) {
        return TypeSelector.binaryName(JavaPattern.exact(PatternDomain.QUALIFIED_NAME, name));
    }

    private static PackageSelector packageName(String name) {
        return PackageSelector.name(JavaPattern.exact(PatternDomain.QUALIFIED_NAME, name));
    }

    private static DependencyEvidence evidence(int line) {
        return new DependencyEvidence(
                LocationId.ofResourcePath("classes/Fixture.class"),
                java.util.Optional.empty(),
                java.util.OptionalInt.empty(),
                java.util.Optional.of("Fixture.java"),
                java.util.OptionalInt.of(line));
    }

    private void writeType(String binaryName, boolean publiclyVisible) throws IOException {
        byte[] bytes = ClassFile.of().build(
                ClassDesc.of(binaryName),
                builder -> builder.withFlags(publiclyVisible ? ClassFile.ACC_PUBLIC : 0));
        Path target = temporaryDirectory.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
