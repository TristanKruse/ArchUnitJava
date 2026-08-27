package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.ClassResourceLocation;
import dev.archunitjava.model.DeclarationLocation;
import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.JavaModuleIdentity;
import dev.archunitjava.model.JavaModuleKind;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.ModuleSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleDescriptorRulesTest {
    @TempDir Path temporaryDirectory;
    private TypeModelResult explicitModel;

    @BeforeEach
    void importDescriptor() throws IOException {
        Files.write(temporaryDirectory.resolve("module-info.class"), descriptor());
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        explicitModel = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void requiresRulesMatchTransitiveAndStaticModifiersExactly() {
        var transitive = ModuleDescriptorRules.requires(
                explicitModel,
                appModule(),
                exact("dep.api"),
                ModuleRequireRuleSpec.required()
                        .withTransitive(DirectiveFlagPolicy.REQUIRED)
                        .withStaticPhase(DirectiveFlagPolicy.FORBIDDEN),
                NonExplicitModulePolicy.REJECT).check();
        var staticPhase = ModuleDescriptorRules.requires(
                explicitModel,
                appModule(),
                exact("dep.optional"),
                ModuleRequireRuleSpec.required()
                        .withStaticPhase(DirectiveFlagPolicy.REQUIRED),
                NonExplicitModulePolicy.REJECT).check();
        var forbidden = ModuleDescriptorRules.requires(
                explicitModel,
                appModule(),
                exact("dep.api"),
                ModuleRequireRuleSpec.no(),
                NonExplicitModulePolicy.REJECT).check();

        assertEquals(RuleStatus.PASSED, transitive.status());
        assertEquals(RuleStatus.PASSED, staticPhase.status());
        assertEquals(RuleStatus.FAILED, forbidden.status());
        assertEquals("true", forbidden.violations().getFirst().attributes().get("transitive"));
        assertEquals("MODULE_DESCRIPTOR",
                forbidden.violations().getFirst().attributes().get("evidenceDomain"));
    }

    @Test
    void qualifiedExportsAndOpensKeepTargetsSeparate() {
        var unqualified = ModuleDescriptorRules.exports(
                explicitModel,
                appModule(),
                exact("app.api"),
                ModulePackageRuleSpec.required()
                        .withQualification(DirectiveQualification.UNQUALIFIED),
                NonExplicitModulePolicy.REJECT).check();
        var qualified = ModuleDescriptorRules.exports(
                explicitModel,
                appModule(),
                exact("app.friend"),
                ModulePackageRuleSpec.required().targetedTo(exact("friend.one")),
                NonExplicitModulePolicy.REJECT).check();
        var open = ModuleDescriptorRules.opens(
                explicitModel,
                appModule(),
                exact("app.reflect"),
                ModulePackageRuleSpec.required().targetedTo(exact("reflect.tool")),
                NonExplicitModulePolicy.REJECT).check();
        var wrongTarget = ModuleDescriptorRules.exports(
                explicitModel,
                appModule(),
                exact("app.friend"),
                ModulePackageRuleSpec.required().targetedTo(exact("friend.missing")),
                NonExplicitModulePolicy.REJECT).check();
        var mixedOnlyTargets = ModuleDescriptorRules.exports(
                explicitModel,
                appModule(),
                exact("app.friend"),
                ModulePackageRuleSpec.only().targetedTo(exact("friend.one")),
                NonExplicitModulePolicy.REJECT).check();

        assertEquals(RuleStatus.PASSED, unqualified.status());
        assertEquals(RuleStatus.PASSED, qualified.status());
        assertEquals(RuleStatus.PASSED, open.status());
        assertEquals(RuleStatus.FAILED, wrongTarget.status());
        assertEquals(RuleStatus.FAILED, mixedOnlyTargets.status());
        assertTrue(mixedOnlyTargets.violations().stream().anyMatch(value ->
                value.attributes().get("declaration").startsWith("app.friend->")));
    }

    @Test
    void usesAndProvidesApplyServiceAndEveryProviderPolicies() {
        var uses = ModuleDescriptorRules.uses(
                explicitModel,
                appModule(),
                exact("svc.Api"),
                ModuleRuleMode.REQUIRED,
                NonExplicitModulePolicy.REJECT).check();
        var allProviders = ModuleDescriptorRules.provides(
                explicitModel,
                appModule(),
                exact("svc.Api"),
                glob("impl.**"),
                ModuleRuleMode.ONLY,
                NonExplicitModulePolicy.REJECT).check();
        var oneProviderOnly = ModuleDescriptorRules.provides(
                explicitModel,
                appModule(),
                exact("svc.Api"),
                exact("impl.One"),
                ModuleRuleMode.ONLY,
                NonExplicitModulePolicy.REJECT).check();

        assertEquals(RuleStatus.PASSED, uses.status());
        assertEquals(RuleStatus.PASSED, allProviders.status());
        assertEquals(RuleStatus.FAILED, oneProviderOnly.status());
        assertEquals("[impl.One, impl.Two]",
                oneProviderOnly.violations().getFirst().attributes().get("providerTypes"));
    }

    @Test
    void automaticAndUnnamedModulesRequireExplicitRejectOrSkipChoice() {
        TypeModelResult mixed = new TypeModelResult(
                explicitModel.types(),
                List.of(
                        explicitModel.modules().getFirst(),
                        emptyModule(JavaModuleIdentity.automatic("auto.module"), "auto.jar", 1),
                        emptyModule(JavaModuleIdentity.unnamed("class-path-2"), "classes", 2)),
                explicitModel.classFileDiagnostics(),
                explicitModel.diagnostics());

        var rejected = ModuleDescriptorRules.requires(
                mixed,
                ModuleSelector.kind(JavaModuleKind.AUTOMATIC),
                exact("anything"),
                ModuleRequireRuleSpec.required(),
                NonExplicitModulePolicy.REJECT).check();
        var skipped = ModuleDescriptorRules.requires(
                mixed,
                ModuleSelector.kind(JavaModuleKind.UNNAMED),
                exact("anything"),
                ModuleRequireRuleSpec.required(),
                NonExplicitModulePolicy.SKIP).check();

        assertEquals(RuleStatus.INCOMPLETE, rejected.status());
        assertEquals(RuleStatus.SKIPPED, skipped.status());
        assertEquals("NO_MODULE_ATTRIBUTE", skipped.diagnostics().getFirst().context().get("reason"));
    }

    @Test
    void declaredReadabilityAndObservedDependenciesCompareWithoutConflation() {
        TypeId app = TypeId.ofBinaryName("app.A");
        TypeId dependency = TypeId.ofBinaryName("dep.B");
        TypeId other = TypeId.ofBinaryName("other.C");
        TypeId unmapped = TypeId.ofBinaryName("unknown.D");
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(app).addNode(dependency).addNode(other).addNode(unmapped)
                .addDependency(app, dependency, DependencyKind.METHOD_CALL, evidence(10))
                .addDependency(app, other, DependencyKind.FIELD_TYPE, evidence(20))
                .addDependency(app, unmapped, DependencyKind.TYPE_REFERENCE, evidence(30))
                .build();
        Map<TypeId, JavaModuleIdentity> mappings = Map.of(
                app, JavaModuleIdentity.explicit("app.module"),
                dependency, JavaModuleIdentity.explicit("dep.api"),
                other, JavaModuleIdentity.explicit("other.module"));

        ModuleDependencyComparison comparison = ModuleDependencyComparisons
                .compareRequiresToObserved(
                        explicitModel,
                        graph,
                        mappings,
                        NonExplicitModulePolicy.REJECT);

        assertEquals(List.of("other.module"), comparison.observedWithoutRequires().stream()
                .map(ModuleDependencyObservation::targetModule).toList());
        assertEquals(List.of("dep.optional"), comparison.requiresWithoutObserved().stream()
                .map(ModuleDependencyObservation::targetModule).toList());
        assertEquals("MODULE_DESCRIPTOR",
                comparison.declaredRequires().getFirst().evidenceDomain());
        assertEquals("OBSERVED_BYTECODE",
                comparison.observedDependencies().getFirst().evidenceDomain());
        assertEquals(1, comparison.unmappedObservedEdges());

        assertThrows(IllegalArgumentException.class, () ->
                ModuleDependencyComparisons.compareRequiresToObserved(
                        explicitModel,
                        graph,
                        Map.of(app, JavaModuleIdentity.unnamed("classes")),
                        NonExplicitModulePolicy.REJECT));
    }

    private byte[] descriptor() {
        ModuleAttribute attribute = ModuleAttribute.of(
                ModuleDesc.of("app.module"),
                module -> module
                        .requires(ModuleDesc.of("dep.api"), ClassFile.ACC_TRANSITIVE, "1")
                        .requires(ModuleDesc.of("dep.optional"), ClassFile.ACC_STATIC_PHASE, "1")
                        .exports(PackageDesc.of("app.api"), 0)
                        .exports(
                                PackageDesc.of("app.friend"),
                                0,
                                ModuleDesc.of("friend.two"),
                                ModuleDesc.of("friend.one"))
                        .opens(
                                PackageDesc.of("app.reflect"),
                                0,
                                ModuleDesc.of("reflect.tool"))
                        .uses(ClassDesc.of("svc.Api"))
                        .provides(
                                ClassDesc.of("svc.Api"),
                                ClassDesc.of("impl.Two"),
                                ClassDesc.of("impl.One")));
        return ClassFile.of().buildModule(attribute);
    }

    private static JavaModule emptyModule(
            JavaModuleIdentity identity, String container, int precedence) {
        return new JavaModule(
                identity,
                0,
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new DeclarationLocation(
                        new ClassResourceLocation(
                                ClassFileInput.Kind.JAR, container, "module-info.class", precedence),
                        Optional.empty()));
    }

    private static ModuleSelector appModule() {
        return ModuleSelector.name(exact("app.module"));
    }

    private static JavaPattern exact(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private static JavaPattern glob(String value) {
        return JavaPattern.glob(PatternDomain.QUALIFIED_NAME, value);
    }

    private static DependencyEvidence evidence(int line) {
        return new DependencyEvidence(
                LocationId.ofResourcePath("classes/Fixture.class"),
                Optional.empty(),
                java.util.OptionalInt.empty(),
                Optional.of("Fixture.java"),
                java.util.OptionalInt.of(line));
    }
}
