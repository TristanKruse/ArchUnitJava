package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ModuleDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicInterfaceRulesTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final MethodTypeDesc NO_ARGS = MethodTypeDesc.of(VOID);
    private static final ClassDesc API = ClassDesc.of("api.Api");
    private static final ClassDesc INTERNAL = ClassDesc.of("internal.Service");
    private static final ClassDesc CLIENT = ClassDesc.of("client.Client");

    @TempDir Path temporaryDirectory;

    @Test
    void approvedPublicAccessPassesWhileInternalAccessNamesOneEntryPoint() throws IOException {
        fixture(temporaryDirectory, 0);
        TypeModelResult model = importInputs(temporaryDirectory);

        var result = rule(model, "useBoth").check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(1, result.violations().size());
        var violation = result.violations().getFirst();
        assertEquals("internal.Service#work()V", violation.attributes().get("target"));
        assertEquals("api.Api#work()V", violation.attributes().get("approvedEntryPoint"));
        assertEquals("true", violation.attributes().get("publicType"));
        assertEquals("true", violation.attributes().get("publicMember"));
        assertEquals("PUBLIC", violation.attributes().get("javaAccess"));
        assertEquals("NOT_RESOLVED", violation.attributes().get("runtimeDispatch"));
    }

    @Test
    void protectedPackageAndNestmateAccessStatesAreNeverReportedAsPublic() throws IOException {
        fixture(temporaryDirectory, ClassFile.ACC_PRIVATE);
        write(temporaryDirectory, "client/Nested.class", ClassFile.of().build(
                ClassDesc.of("client.Nested"), builder -> builder
                        .with(NestHostAttribute.of(INTERNAL))
                        .withMethodBody("use", NO_ARGS, ClassFile.ACC_PUBLIC,
                                code -> code.invokestatic(INTERNAL, "hidden", NO_ARGS).return_())));
        TypeModelResult model = importInputs(temporaryDirectory);

        var nested = PublicInterfaceRules.onlyAccessApprovedInterfaces(
                model,
                member("client.Nested", "use"),
                binary("api.Api"),
                member("api.Api", "work")).check();

        assertEquals("PRIVATE_NESTMATE",
                nested.violations().getFirst().attributes().get("javaAccess"));
        assertEquals("true", nested.violations().getFirst().attributes().get("nestmate"));

        fixture(temporaryDirectory, ClassFile.ACC_PROTECTED);
        var protectedResult = rule(importInputs(temporaryDirectory), "useHidden").check();
        assertEquals("PROTECTED_CROSS_PACKAGE",
                protectedResult.violations().getFirst().attributes().get("javaAccess"));
        assertEquals("false", protectedResult.violations().getFirst().attributes().get("publicMember"));

        fixture(temporaryDirectory, 0);
        var packageResult = rule(importInputs(temporaryDirectory), "useHidden").check();
        assertEquals("PACKAGE_PRIVATE_INACCESSIBLE",
                packageResult.violations().getFirst().attributes().get("javaAccess"));
    }

    @Test
    void jpmsExportsAreEvaluatedSeparatelyFromJavaVisibility() throws IOException {
        Path provider = temporaryDirectory.resolve("provider");
        Path consumer = temporaryDirectory.resolve("consumer");
        fixture(provider, ClassFile.ACC_PUBLIC);
        Files.createDirectories(consumer.resolve("client"));
        Files.move(provider.resolve("client/Client.class"), consumer.resolve("client/Client.class"));
        write(provider, "module-info.class", module("provider.module", false));
        write(consumer, "module-info.class", module("consumer.module", true));
        TypeModelResult model = importInputs(provider, consumer);

        var result = rule(model, "useBoth").check();

        assertEquals(2, result.violations().size());
        assertTrue(result.violations().stream().allMatch(value ->
                value.attributes().get("moduleExport").equals("PACKAGE_NOT_EXPORTED")));
        assertTrue(result.violations().stream().allMatch(value ->
                value.attributes().get("javaAccess").equals("PUBLIC")));
    }

    @Test
    void blindSpotsAndUnresolvedTargetsAreExplicitDiagnostics() throws IOException {
        fixture(temporaryDirectory, ClassFile.ACC_PUBLIC);
        TypeModelResult model = importInputs(temporaryDirectory);

        var result = rule(model, "useBoth").check();

        var blindSpots = result.diagnostics().stream()
                .filter(value -> value.code().equals(PublicInterfaceRules.BLIND_SPOTS_DIAGNOSTIC))
                .findFirst().orElseThrow();
        assertEquals("NOT_ANALYZED", blindSpots.context().get("reflection"));
        assertEquals("NOT_MODELED", blindSpots.context().get("runtimeAddExports"));
        assertTrue(result.diagnostics().stream()
                .anyMatch(value -> value.code().equals("public-interface.unresolved-targets")));
    }

    private ArchitectureRule rule(TypeModelResult model, String caller) {
        return PublicInterfaceRules.onlyAccessApprovedInterfaces(
                model,
                member("client.Client", caller),
                binary("api.Api"),
                member("api.Api", "work"));
    }

    private void fixture(Path root, int hiddenFlags) throws IOException {
        write(root, "api/Api.class", ClassFile.of().build(API, builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("work", NO_ARGS, ClassFile.ACC_PUBLIC, code -> code.return_())));
        write(root, "internal/Service.class", ClassFile.of().build(INTERNAL, builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("work", NO_ARGS, ClassFile.ACC_PUBLIC, code -> code.return_())
                .withMethodBody("hidden", NO_ARGS, hiddenFlags | ClassFile.ACC_STATIC,
                        code -> code.return_())));
        write(root, "client/Client.class", ClassFile.of().build(CLIENT, builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("useBoth", NO_ARGS, ClassFile.ACC_PUBLIC,
                        code -> code.new_(API).dup().invokespecial(API, "<init>", NO_ARGS).pop()
                                .new_(API).invokevirtual(API, "work", NO_ARGS)
                                .new_(INTERNAL).invokevirtual(INTERNAL, "work", NO_ARGS)
                                .invokestatic(ClassDesc.of("missing.External"), "run", NO_ARGS)
                                .return_())
                .withMethodBody("useHidden", NO_ARGS, ClassFile.ACC_PUBLIC,
                        code -> code.invokestatic(INTERNAL, "hidden", NO_ARGS).return_())));
    }

    private static byte[] module(String name, boolean exportClient) {
        ModuleAttribute attribute = ModuleAttribute.of(ModuleDesc.of(name), builder -> {
            if (exportClient) builder.exports(
                    java.lang.constant.PackageDesc.of("client"), 0);
        });
        return ClassFile.of().buildModule(attribute);
    }

    private TypeModelResult importInputs(Path... roots) throws IOException {
        var inputs = Arrays.stream(roots).map(ClassFileInput::directory).toList();
        var resources = new ClassFileInputEnumerator().enumerate(inputs).resources();
        return new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    private static TypeSelector binary(String name) {
        return TypeSelector.binaryName(JavaPattern.exact(PatternDomain.QUALIFIED_NAME, name));
    }

    private static MemberSelector member(String owner, String name) {
        return MemberSelector.allOf(
                MemberSelector.declaredBy(binary(owner)),
                MemberSelector.named(name));
    }

    private static void write(Path root, String name, byte[] bytes) throws IOException {
        Path target = root.resolve(name);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
