package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoverageRulesTest {
    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importApplicationAndDependencyTypes() throws IOException {
        Path classes = temporaryDirectory.resolve("classes");
        writeClass(classes, "app.ui.Controller", ClassFile.ACC_PUBLIC);
        writeClass(classes, "app.service.Service", ClassFile.ACC_PUBLIC);
        writeClass(classes, "app.generated.Helper", ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC);
        Path dependency = temporaryDirectory.resolve("vendor.jar");
        writeJarClass(dependency, "vendor.Client", ClassFile.ACC_PUBLIC);
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(classes), ClassFileInput.jar(dependency)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void typeCoverageReportsUnassignedAndMultiplyAssignedSeparately() {
        var result = CoverageRules.types(
                model,
                TypeSelector.all(),
                List.of(
                        TypeCoveragePolicy.named(
                                "application",
                                packages("app.**").excluding(TypeSelector.modifier(
                                        dev.archunitjava.model.JavaModifier.SYNTHETIC))),
                        TypeCoveragePolicy.named("ui", packages("app.ui"))))
                .check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(List.of(
                "coverage.multiply-assigned",
                "coverage.unassigned"),
                result.violations().stream().map(value -> value.code()).distinct().sorted().toList());
        assertEquals("[application, ui]", result.violations().stream()
                .filter(value -> value.code().equals("coverage.multiply-assigned"))
                .findFirst().orElseThrow().attributes().get("assignments"));
        assertEquals("type:vendor.Client", result.violations().stream()
                .filter(value -> value.code().equals("coverage.unassigned"))
                .filter(value -> value.subjects().getFirst().id().stableKey().contains("vendor"))
                .findFirst().orElseThrow().subjects().getFirst().id().stableKey());
        assertEquals(2, result.violations().stream()
                .filter(value -> value.code().equals("coverage.unassigned")).count());
    }

    @Test
    void generatedAndDependencyInputsNeedExplicitExclusions() {
        TypeSelector exclusions = TypeSelector.anyOf(
                TypeSelector.modifier(dev.archunitjava.model.JavaModifier.SYNTHETIC),
                TypeSelector.inputKind(ClassFileInput.Kind.JAR));

        var result = CoverageRules.types(
                model,
                TypeSelector.all(),
                exclusions,
                List.of(
                        TypeCoveragePolicy.named("ui", packages("app.ui")),
                        TypeCoveragePolicy.named("service", packages("app.service"))))
                .check();

        assertEquals(RuleStatus.PASSED, result.status());
    }

    @Test
    void packageCoverageUsesTheSameSubjectAndExclusionSemantics() {
        var result = CoverageRules.packages(
                model,
                PackageSelector.all(),
                List.of(
                        PackageCoveragePolicy.named("application", packageNames("app.**")),
                        PackageCoveragePolicy.named("ui", packageNames("app.ui"))))
                .check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(List.of(
                "coverage.multiply-assigned",
                "coverage.unassigned"),
                result.violations().stream().map(value -> value.code()).distinct().sorted().toList());
        assertEquals("package:vendor", result.violations().stream()
                .filter(value -> value.code().equals("coverage.unassigned"))
                .findFirst().orElseThrow().subjects().getFirst().id().stableKey());

        var excluded = CoverageRules.packages(
                model,
                PackageSelector.all(),
                packageNames("vendor"),
                List.of(
                        PackageCoveragePolicy.named("application", packageNames("app.**"))))
                .check();
        assertEquals(RuleStatus.PASSED, excluded.status());
    }

    @Test
    void moduleCoveragePreservesExplicitAutomaticAndUnnamedIdentities() {
        TypeModelResult modules = new TypeModelResult(
                model.types(),
                List.of(
                        module(JavaModuleIdentity.explicit("app.module"), 0),
                        module(JavaModuleIdentity.automatic("vendor.module"), 1),
                        module(JavaModuleIdentity.unnamed("class-path"), 2)),
                model.classFileDiagnostics(),
                model.diagnostics());
        var result = CoverageRules.modules(
                modules,
                ModuleSelector.all(),
                List.of(
                        ModuleCoveragePolicy.named(
                                "explicit", ModuleSelector.kind(JavaModuleKind.EXPLICIT)),
                        ModuleCoveragePolicy.named(
                                "application", ModuleSelector.name(exact("app.module")))))
                .check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(1, result.violations().stream()
                .filter(value -> value.code().equals("coverage.multiply-assigned")).count());
        assertEquals(2, result.violations().stream()
                .filter(value -> value.code().equals("coverage.unassigned")).count());

        var explicitlyExcluded = CoverageRules.modules(
                modules,
                ModuleSelector.all(),
                ModuleSelector.kind(JavaModuleKind.UNNAMED),
                List.of(
                        ModuleCoveragePolicy.named(
                                "explicit", ModuleSelector.kind(JavaModuleKind.EXPLICIT)),
                        ModuleCoveragePolicy.named(
                                "automatic", ModuleSelector.kind(JavaModuleKind.AUTOMATIC))))
                .check();
        assertEquals(RuleStatus.PASSED, explicitlyExcluded.status());
    }

    @Test
    void duplicatePolicyNamesAreRejectedInsteadOfSilentlyMerged() {
        assertThrows(IllegalArgumentException.class, () -> CoverageRules.types(
                model,
                TypeSelector.all(),
                List.of(
                        TypeCoveragePolicy.named("same", TypeSelector.all()),
                        TypeCoveragePolicy.named("same", TypeSelector.none()))));
        assertThrows(NullPointerException.class, () -> CoverageRules.modules(
                new TypeModelResult(List.of(), List.of(), List.of()),
                ModuleSelector.all(),
                null,
                List.of(ModuleCoveragePolicy.named("all", ModuleSelector.all()))));
    }

    private static TypeSelector packages(String expression) {
        return TypeSelector.packageName(JavaPattern.glob(PatternDomain.QUALIFIED_NAME, expression));
    }

    private static PackageSelector packageNames(String expression) {
        return PackageSelector.name(JavaPattern.glob(PatternDomain.QUALIFIED_NAME, expression));
    }

    private static JavaPattern exact(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private static JavaModule module(JavaModuleIdentity identity, int precedence) {
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
                                ClassFileInput.Kind.JAR,
                                "module-" + precedence + ".jar",
                                "module-info.class",
                                precedence),
                        Optional.empty()));
    }

    private static void writeClass(Path root, String binaryName, int flags) throws IOException {
        Path target = root.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, classBytes(binaryName, flags));
    }

    private static void writeJarClass(Path jar, String binaryName, int flags) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(binaryName.replace('.', '/') + ".class"));
            output.write(classBytes(binaryName, flags));
            output.closeEntry();
        }
    }

    private static byte[] classBytes(String binaryName, int flags) {
        return ClassFile.of().build(
                ClassDesc.of(binaryName), builder -> builder.withFlags(flags));
    }
}
