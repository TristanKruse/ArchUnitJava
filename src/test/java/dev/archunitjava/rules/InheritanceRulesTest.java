package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.ExternalTypeStub;
import dev.archunitjava.model.JavaTypeKind;
import dev.archunitjava.model.TypeHierarchy;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InheritanceRulesTest {
    @TempDir Path temporaryDirectory;
    private TypeModelResult model;
    private TypeHierarchy hierarchy;

    @BeforeEach
    void importFixture() throws IOException {
        write("api/Contract.class", ClassFile.of().build(
                ClassDesc.of("api.Contract"), builder -> builder
                        .withFlags(AccessFlag.PUBLIC, AccessFlag.INTERFACE, AccessFlag.ABSTRACT)));
        write("impl/Base.class", ClassFile.of().build(
                ClassDesc.of("impl.Base"), builder -> {}));
        write("impl/Child.class", ClassFile.of().build(
                ClassDesc.of("impl.Child"), builder -> builder
                        .withSuperclass(ClassDesc.of("impl.Base"))
                        .withInterfaceSymbols(ClassDesc.of("api.Contract"))));
        write("impl/GrandChild.class", ClassFile.of().build(
                ClassDesc.of("impl.GrandChild"), builder -> builder
                        .withSuperclass(ClassDesc.of("impl.Child"))));
        write("impl/UnknownChild.class", ClassFile.of().build(
                ClassDesc.of("impl.UnknownChild"), builder -> builder
                        .withSuperclass(ClassDesc.of("external.Missing"))));
        write("sealed/Root.class", ClassFile.of().build(
                ClassDesc.of("sealed.Root"), builder -> builder
                        .with(PermittedSubclassesAttribute.ofSymbols(
                                ClassDesc.of("sealed.Allowed"),
                                ClassDesc.of("sealed.Missing")))));
        write("sealed/Allowed.class", ClassFile.of().build(
                ClassDesc.of("sealed.Allowed"), builder -> builder
                        .withSuperclass(ClassDesc.of("sealed.Root"))));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
        hierarchy = TypeHierarchy.builder()
                .addImported(model.types())
                .addExternal(ExternalTypeStub.complete(
                        "java.lang.Object", JavaTypeKind.CLASS, null, List.of()))
                .build();
    }

    @Test
    void directAndTransitiveExtendsImplementsAndAssignabilityStayDistinct() {
        assertEquals(RuleStatus.PASSED, rule(
                "impl.Child", "impl.Base",
                HierarchyRuleSpec.direct(
                        HierarchyRelation.EXTENDS, HierarchyRuleMode.MUST_MATCH)).check().status());
        assertEquals(RuleStatus.FAILED, rule(
                "impl.GrandChild", "impl.Base",
                HierarchyRuleSpec.direct(
                        HierarchyRelation.EXTENDS, HierarchyRuleMode.MUST_MATCH)).check().status());
        assertEquals(RuleStatus.PASSED, rule(
                "impl.GrandChild", "impl.Base",
                HierarchyRuleSpec.transitive(
                        HierarchyRelation.EXTENDS, HierarchyRuleMode.MUST_MATCH)).check().status());
        assertEquals(RuleStatus.FAILED, rule(
                "impl.GrandChild", "api.Contract",
                HierarchyRuleSpec.direct(
                        HierarchyRelation.IMPLEMENTS, HierarchyRuleMode.MUST_MATCH)).check().status());
        assertEquals(RuleStatus.PASSED, rule(
                "impl.GrandChild", "api.Contract",
                HierarchyRuleSpec.transitive(
                        HierarchyRelation.IMPLEMENTS, HierarchyRuleMode.MUST_MATCH)).check().status());

        var forbidden = rule(
                "impl.GrandChild", "api.Contract",
                HierarchyRuleSpec.transitive(
                        HierarchyRelation.ASSIGNABLE_TO, HierarchyRuleMode.MUST_NOT_MATCH)).check();
        assertEquals(RuleStatus.FAILED, forbidden.status());
        assertEquals(
                "impl.GrandChild -> impl.Child -> api.Contract",
                forbidden.violations().getFirst().attributes().get("path"));
        assertFalse(forbidden.violations().getFirst().evidence().isEmpty());
    }

    @Test
    void unknownExternalAncestorsCannotSilentlyPassStrictTransitiveRules() {
        ArchitectureRule strict = rule(
                "impl.UnknownChild", "api.Contract",
                HierarchyRuleSpec.transitive(
                        HierarchyRelation.ASSIGNABLE_TO, HierarchyRuleMode.MUST_NOT_MATCH));

        assertEquals(RuleStatus.INCOMPLETE, strict.check().status());
        assertEquals("rule.hierarchy.unknown", strict.check().diagnostics().getFirst().code());
        assertEquals(RuleStatus.PASSED, strict.check(CheckOptions.builder()
                .allowIncompleteAnalysis(true).build()).status());
        assertEquals(RuleStatus.PASSED, rule(
                "impl.UnknownChild", "api.Contract",
                HierarchyRuleSpec.transitive(
                        HierarchyRelation.ASSIGNABLE_TO, HierarchyRuleMode.MUST_NOT_MATCH)
                        .withUnknownHierarchy(UnknownInheritancePolicy.IGNORE)).check().status());
    }

    @Test
    void sealedViolationsRetainPermittedSubclassPathAndDeclarationEvidence() {
        var forbidden = rule(
                "sealed.Root", "sealed.Allowed",
                HierarchyRuleSpec.permitting(HierarchyRuleMode.MUST_NOT_MATCH)).check();

        assertEquals(RuleStatus.FAILED, forbidden.status());
        assertEquals(
                "sealed.Root -> sealed.Allowed",
                forbidden.violations().getFirst().attributes().get("path"));
        assertEquals("PERMITTED_SUBCLASS",
                forbidden.violations().getFirst().attributes().get("terminalRelationship"));
        assertFalse(forbidden.violations().getFirst().evidence().isEmpty());
    }

    private ArchitectureRule rule(
            String subject, String target, HierarchyRuleSpec spec) {
        return InheritanceRules.types(
                model, hierarchy, binary(subject), binary(target), spec);
    }

    private static TypeSelector binary(String name) {
        return TypeSelector.binaryName(
                JavaPattern.exact(PatternDomain.QUALIFIED_NAME, name));
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
