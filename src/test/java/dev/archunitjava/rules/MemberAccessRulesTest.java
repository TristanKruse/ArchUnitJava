package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.JavaCodeAccessKind;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberAccessRulesTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc SERVICE = ClassDesc.of("target.Service");
    private static final ClassDesc API = ClassDesc.of("target.Api");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importFixture() throws IOException {
        write("target/Service.class", ClassFile.of().build(SERVICE, builder -> builder
                .withField("count", INT, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC)
                .withField("value", INT, ClassFile.ACC_PUBLIC)
                .withMethodBody("<init>", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC, code -> code.return_())
                .withMethodBody("staticWork", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.return_())
                .withMethodBody("work", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC, code -> code.return_())));
        write("target/Api.class", ClassFile.of().build(API, builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT)
                .withMethod("run", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {})));
        write("client/Client.class", ClassFile.of().build(
                ClassDesc.of("client.Client"), builder -> builder
                        .with(SourceFileAttribute.of("Client.java"))
                        .withMethodBody(
                                "exercise", MethodTypeDesc.of(VOID),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                code -> code.lineNumber(40)
                                        .getstatic(SERVICE, "count", INT).pop()
                                        .iconst_1().putstatic(SERVICE, "count", INT)
                                        .aconst_null().getfield(SERVICE, "value", INT).pop()
                                        .aconst_null().iconst_1().putfield(SERVICE, "value", INT)
                                        .new_(SERVICE).dup()
                                        .invokespecial(SERVICE, "<init>", MethodTypeDesc.of(VOID)).pop()
                                        .invokestatic(SERVICE, "staticWork", MethodTypeDesc.of(VOID))
                                        .aconst_null().invokevirtual(
                                                SERVICE, "work", MethodTypeDesc.of(VOID))
                                        .aconst_null().invokeinterface(
                                                API, "run", MethodTypeDesc.of(VOID))
                                        .aconst_null().invokevirtual(
                                                ClassDesc.ofDescriptor("[Ljava/lang/String;"),
                                                "clone", MethodTypeDesc.of(OBJECT))
                                        .pop().return_())
                        .withMethodBody(
                                "idle", MethodTypeDesc.of(VOID),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                                code -> code.return_())
                        .withMethodBody(
                                "generatedExercise", MethodTypeDesc.of(VOID),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC,
                                code -> code.invokestatic(
                                        SERVICE, "staticWork", MethodTypeDesc.of(VOID)).return_())));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void callConstructorReadAndWritePoliciesAreIndependent() {
        MemberSelector exercise = caller("exercise");

        assertEquals(1, MemberAccessRules.accesses(
                model, exercise, target("target.Service", "work"),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.METHOD_CALL))
                .check().violations().size());
        assertEquals(RuleStatus.PASSED, MemberAccessRules.accesses(
                model, exercise, target("target.Service", "work"),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.FIELD_READ))
                .check().status());
        assertEquals(1, MemberAccessRules.accesses(
                model, exercise,
                MemberSelector.allOf(
                        MemberSelector.declaredBy(binary("target.Service")),
                        MemberSelector.constructors()),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.CONSTRUCTOR_CALL))
                .check().violations().size());
        assertEquals(2, MemberAccessRules.accesses(
                model, exercise, fieldsOnService(),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.FIELD_READ))
                .check().violations().size());
        assertEquals(2, MemberAccessRules.accesses(
                model, exercise, fieldsOnService(),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.FIELD_WRITE))
                .check().violations().size());
    }

    @Test
    void onlyAnyAndPerCallerRequiredModesUseSymbolicTargets() {
        MemberSelector serviceMethods = MemberSelector.allOf(
                MemberSelector.declaredBy(binary("target.Service")),
                MemberSelector.methods());
        var only = MemberAccessRules.accesses(
                model, caller("exercise"), serviceMethods,
                MemberAccessRuleSpec.only(JavaCodeAccessKind.METHOD_CALL)).check();
        var any = MemberAccessRules.accesses(
                model, caller("exercise"), target("target.Service", "staticWork"),
                MemberAccessRuleSpec.any(JavaCodeAccessKind.METHOD_CALL)).check();
        var required = MemberAccessRules.accesses(
                model,
                MemberSelector.allOf(
                        MemberSelector.declaredBy(binary("client.Client")),
                        MemberSelector.codeUnits()),
                target("target.Service", "staticWork"),
                MemberAccessRuleSpec.required(JavaCodeAccessKind.METHOD_CALL)).check();

        assertEquals(RuleStatus.FAILED, only.status());
        assertEquals(2, only.violations().size());
        assertEquals(RuleStatus.PASSED, any.status());
        assertEquals(RuleStatus.FAILED, required.status());
        assertEquals(List.of("member:client.Client#idle()V"), required.violations().stream()
                .map(value -> value.subjects().getFirst().id().stableKey()).toList());
    }

    @Test
    void violationsRetainCallerTargetOpcodeAndExactLocationWithoutDispatchClaims() {
        var result = MemberAccessRules.accesses(
                model,
                caller("exercise"),
                target("target.Service", "work"),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.METHOD_CALL)).check();
        var violation = result.violations().getFirst();

        assertEquals("client.Client#exercise()V", violation.attributes().get("caller"));
        assertEquals("target.Service", violation.attributes().get("targetOwner"));
        assertEquals("work", violation.attributes().get("targetName"));
        assertEquals("()V", violation.attributes().get("targetDescriptor"));
        assertEquals("INVOKEVIRTUAL", violation.attributes().get("opcode"));
        assertEquals("METHOD_CALL", violation.attributes().get("accessKind"));
        assertEquals("Client.java", violation.attributes().get("sourceFile"));
        assertEquals("40", violation.attributes().get("lineNumber"));
        assertEquals("SYMBOLIC_CONSTANT_POOL_TARGET",
                violation.attributes().get("resolution"));
        assertEquals("NOT_RESOLVED", violation.attributes().get("runtimeDispatch"));
        assertTrue(violation.evidence().getFirst().bytecodeOffset().isPresent());
        assertTrue(violation.evidence().getFirst().ownerMember().isPresent());
    }

    @Test
    void compilerCreatedCallersRequireExplicitInclusion() {
        ArchitectureRule ignored = MemberAccessRules.accesses(
                model,
                caller("generatedExercise"),
                target("target.Service", "staticWork"),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.METHOD_CALL));
        ArchitectureRule included = MemberAccessRules.accesses(
                model,
                caller("generatedExercise"),
                target("target.Service", "staticWork"),
                MemberAccessRuleSpec.no(JavaCodeAccessKind.METHOD_CALL)
                        .withCompilerAccesses(CompilerAccessPolicy.INCLUDE));

        assertEquals(RuleStatus.INCOMPLETE, ignored.check().status());
        assertEquals(RuleStatus.FAILED, included.check().status());
        assertEquals("true", included.check().violations().getFirst()
                .attributes().get("compilerCreated"));
    }

    private MemberSelector caller(String name) {
        return MemberSelector.allOf(
                MemberSelector.declaredBy(binary("client.Client")),
                MemberSelector.named(name));
    }

    private static MemberSelector target(String owner, String name) {
        return MemberSelector.allOf(
                MemberSelector.declaredBy(binary(owner)),
                MemberSelector.named(name));
    }

    private static MemberSelector fieldsOnService() {
        return MemberSelector.allOf(
                MemberSelector.declaredBy(binary("target.Service")),
                MemberSelector.fields());
    }

    private static TypeSelector binary(String binaryName) {
        return TypeSelector.binaryName(
                JavaPattern.exact(PatternDomain.QUALIFIED_NAME, binaryName));
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
