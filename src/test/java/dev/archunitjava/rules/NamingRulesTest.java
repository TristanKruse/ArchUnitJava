package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.EnclosingMethodAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NamingRulesTest {
    private static final ClassDesc HOST = ClassDesc.of("api.Host");
    private static final MethodTypeDesc VOID_METHOD = MethodTypeDesc.ofDescriptor("()V");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importFixture() throws IOException {
        write("api/GoodName.class", ClassFile.of().build(
                ClassDesc.of("api.GoodName"), builder -> builder
                        .with(SourceFileAttribute.of("GoodName.java"))
                        .withMethod("run_bad", VOID_METHOD,
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {})));
        write("api/Bad_name.class", ClassFile.of().build(
                ClassDesc.of("api.Bad_name"), builder -> builder
                        .with(SourceFileAttribute.of("Bad_name.java"))));
        write("api/Host.class", ClassFile.of().build(HOST, builder -> {}));
        write("api/Host$1.class", nested(
                "api.Host$1",
                InnerClassInfo.of(
                        ClassDesc.of("api.Host$1"), Optional.empty(), Optional.empty(), 0)));
        write("api/Host$1Local.class", nested(
                "api.Host$1Local",
                InnerClassInfo.of(
                        ClassDesc.of("api.Host$1Local"), Optional.empty(), Optional.of("Local"), 0)));
        write("generated/AdapterGenerated.class", ClassFile.of().build(
                ClassDesc.of("generated.AdapterGenerated"),
                builder -> builder.withFlags(ClassFile.ACC_SYNTHETIC)));
        write("Unpackaged.class", ClassFile.of().build(
                ClassDesc.of("Unpackaged"), builder -> {}));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void nameAndLocationTargetsRemainDistinctAndDomainChecked() {
        TypeSelector good = binaryName("api.GoodName");
        String container = model.types().stream()
                .filter(type -> type.binaryName().equals("api.GoodName"))
                .findFirst().orElseThrow().location().resource().container();

        assertPassed(NamingRules.types(model, good, NamingTarget.SIMPLE_NAME,
                exactName("GoodName"), PatternRuleMode.MUST_MATCH));
        assertPassed(NamingRules.types(model, good, NamingTarget.BINARY_NAME,
                exactName("api.GoodName"), PatternRuleMode.MUST_MATCH));
        assertPassed(NamingRules.types(model, good, NamingTarget.PACKAGE_NAME,
                exactName("api"), PatternRuleMode.MUST_MATCH));
        assertPassed(NamingRules.types(model, good, NamingTarget.SOURCE_FILE,
                exactPath("GoodName.java"), PatternRuleMode.MUST_MATCH));
        assertPassed(NamingRules.types(model, good, NamingTarget.CLASS_RESOURCE,
                exactPath("api/GoodName.class"), PatternRuleMode.MUST_MATCH));
        assertPassed(NamingRules.types(model, good, NamingTarget.ARTIFACT_CONTAINER,
                exactPath(container), PatternRuleMode.MUST_MATCH));

        assertThrows(IllegalArgumentException.class, () -> NamingRules.types(
                model, good, NamingTarget.CLASS_RESOURCE,
                exactName("api.GoodName"), PatternRuleMode.MUST_MATCH));
        assertThrows(IllegalArgumentException.class, () -> NamingRules.members(
                model, MemberSelector.all(), NamingTarget.BINARY_NAME,
                exactName("api.GoodName"), PatternRuleMode.MUST_MATCH));

        var unnamed = NamingRules.packages(
                model, PackageSelector.unnamed(), NamingTarget.PACKAGE_NAME,
                JavaPattern.regex(PatternDomain.QUALIFIED_NAME, ".+"),
                PatternRuleMode.MUST_MATCH).check();
        assertEquals("<unnamed>", unnamed.violations().getFirst().attributes().get("actual"));
    }

    @Test
    void positiveAndNegativeRulesReturnEveryViolationInStableOrder() {
        JavaPattern conventional = JavaPattern.regex(
                PatternDomain.QUALIFIED_NAME, "[A-Z][A-Za-z0-9]*");
        var types = NamingRules.types(
                model, TypeSelector.packageName(exactName("api")),
                NamingTarget.SIMPLE_NAME, conventional, PatternRuleMode.MUST_MATCH).check();
        var members = NamingRules.members(
                model, MemberSelector.all(),
                NamingTarget.SIMPLE_NAME,
                JavaPattern.glob(PatternDomain.QUALIFIED_NAME, "*_bad"),
                PatternRuleMode.MUST_NOT_MATCH).check();
        var packages = NamingRules.packages(
                model, PackageSelector.all(),
                NamingTarget.PACKAGE_NAME,
                exactName("generated"),
                PatternRuleMode.MUST_NOT_MATCH).check();

        assertEquals(List.of("type:api.Bad_name"), types.violations().stream()
                .map(value -> value.subjects().getFirst().id().stableKey()).toList());
        assertEquals(List.of("member:api.GoodName#run_bad()V"), members.violations().stream()
                .map(value -> value.subjects().getFirst().id().stableKey()).toList());
        assertEquals(List.of("package:generated"), packages.violations().stream()
                .map(value -> value.subjects().getFirst().id().stableKey()).toList());
        assertTrue(types.violations().stream().allMatch(value -> !value.evidence().isEmpty()));
        assertEquals(types, NamingRules.types(
                model, TypeSelector.packageName(exactName("api")),
                NamingTarget.SIMPLE_NAME, conventional, PatternRuleMode.MUST_MATCH).check());
    }

    @Test
    void anonymousLocalAndGeneratedTypesRequireExplicitInclusion() {
        ArchitectureRule anonymous = NamingRules.types(
                model, binaryName("api.Host$1"),
                NamingTarget.SIMPLE_NAME, exactName("Anything"), PatternRuleMode.MUST_MATCH);
        ArchitectureRule local = NamingRules.types(
                model, binaryName("api.Host$1Local"),
                NamingTarget.SIMPLE_NAME, exactName("Local"), PatternRuleMode.MUST_NOT_MATCH);
        ArchitectureRule generated = NamingRules.types(
                model, binaryName("generated.AdapterGenerated"),
                NamingTarget.SIMPLE_NAME,
                exactName("AdapterGenerated"), PatternRuleMode.MUST_NOT_MATCH);

        assertEquals(RuleStatus.INCOMPLETE, anonymous.check().status());
        assertEquals(RuleStatus.INCOMPLETE, local.check().status());
        assertEquals(RuleStatus.INCOMPLETE, generated.check().status());
        assertEquals(RuleStatus.FAILED, NamingRules.types(
                model, binaryName("api.Host$1"),
                NamingTarget.SIMPLE_NAME, exactName("Anything"), PatternRuleMode.MUST_MATCH,
                NamingRuleOptions.defaults().includingAnonymousTypes()).check().status());
        assertEquals(RuleStatus.FAILED, NamingRules.types(
                model, binaryName("api.Host$1Local"),
                NamingTarget.SIMPLE_NAME, exactName("Local"), PatternRuleMode.MUST_NOT_MATCH,
                NamingRuleOptions.defaults().includingLocalTypes()).check().status());
        assertEquals(RuleStatus.FAILED, NamingRules.types(
                model, binaryName("generated.AdapterGenerated"),
                NamingTarget.SIMPLE_NAME,
                exactName("AdapterGenerated"), PatternRuleMode.MUST_NOT_MATCH,
                NamingRuleOptions.defaults().includingGeneratedTypes()).check().status());
    }

    private byte[] nested(String binaryName, InnerClassInfo info) {
        return ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder
                .with(InnerClassesAttribute.of(info))
                .with(EnclosingMethodAttribute.of(
                        HOST, Optional.of("run"), Optional.of(VOID_METHOD))));
    }

    private static TypeSelector binaryName(String value) {
        return TypeSelector.binaryName(exactName(value));
    }

    private static JavaPattern exactName(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private static JavaPattern exactPath(String value) {
        return JavaPattern.exact(PatternDomain.RESOURCE_PATH, value);
    }

    private static void assertPassed(ArchitectureRule rule) {
        assertEquals(RuleStatus.PASSED, rule.check().status());
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
