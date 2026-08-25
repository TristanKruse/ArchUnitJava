package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleModelTest {
    @TempDir Path temporaryDirectory;

    @Test
    void explicitModuleDirectivesAndModifierMasksAreLossless() throws IOException {
        JavaModule module = importModule(moduleInfo()).modules().getFirst();

        assertEquals(JavaModuleKind.EXPLICIT, module.identity().kind());
        assertEquals("com.example.app", module.identity().name().orElseThrow());
        assertEquals(ClassFile.ACC_OPEN | ClassFile.ACC_SYNTHETIC, module.flags());
        assertTrue(module.open());
        assertEquals("1.2.3", module.version().orElseThrow());

        JavaModuleRequire requires = module.requires().getFirst();
        assertEquals("missing.dependency", requires.moduleName());
        assertEquals(
                ClassFile.ACC_TRANSITIVE | ClassFile.ACC_STATIC_PHASE | ClassFile.ACC_SYNTHETIC,
                requires.flags());
        assertTrue(requires.transitive());
        assertTrue(requires.staticPhase());
        assertEquals("9", requires.compiledVersion().orElseThrow());
    }

    @Test
    void qualifiedExportsOpensUsesAndProvidersRemainDeclaredData() throws IOException {
        JavaModule module = importModule(moduleInfo()).modules().getFirst();

        JavaModulePackageDirective exports = module.exports().getFirst();
        assertEquals("com.example.api", exports.packageName().value());
        assertTrue(exports.qualified());
        assertEquals(List.of("consumer.alpha", "consumer.zeta"), exports.targetModules());
        assertEquals(ClassFile.ACC_SYNTHETIC, exports.flags());

        JavaModulePackageDirective opens = module.opens().getFirst();
        assertEquals("com.example.internal", opens.packageName().value());
        assertEquals(List.of("reflective.consumer"), opens.targetModules());
        assertEquals(ClassFile.ACC_MANDATED, opens.flags());

        assertEquals(List.of("missing.Service"), module.uses().stream()
                .map(JvmReferenceType::binaryName)
                .toList());
        JavaModuleProvide provide = module.provides().getFirst();
        assertEquals("missing.Service", provide.service().binaryName());
        assertEquals(List.of("impl.First", "impl.Second"), provide.providers().stream()
                .map(JvmReferenceType::binaryName)
                .toList());
    }

    @Test
    void explicitAutomaticAndUnnamedIdentitiesCannotCollapse() {
        JavaModuleIdentity explicit = JavaModuleIdentity.explicit("same.name");
        JavaModuleIdentity automatic = JavaModuleIdentity.automatic("same.name");
        JavaModuleIdentity unnamed = JavaModuleIdentity.unnamed("classpath-entry-0");

        assertNotEquals(explicit, automatic);
        assertNotEquals(automatic, unnamed);
        assertEquals(JavaModuleKind.UNNAMED, unnamed.kind());
        assertTrue(unnamed.name().isEmpty());
        assertEquals("classpath-entry-0", unnamed.unnamedOrigin().orElseThrow());
    }

    @Test
    void moduleInfoIsNotATypeAndCollectionsAreImmutable() throws IOException {
        TypeModelResult result = importModule(moduleInfo());

        assertTrue(result.types().isEmpty());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.modules().size());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> result.modules().getFirst().requires().clear());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> result.modules().clear());
    }

    private byte[] moduleInfo() {
        ModuleAttribute attribute = ModuleAttribute.of(
                ModuleDesc.of("com.example.app"),
                module -> module
                        .moduleFlags(ClassFile.ACC_OPEN | ClassFile.ACC_SYNTHETIC)
                        .moduleVersion("1.2.3")
                        .requires(
                                ModuleDesc.of("missing.dependency"),
                                ClassFile.ACC_TRANSITIVE
                                        | ClassFile.ACC_STATIC_PHASE
                                        | ClassFile.ACC_SYNTHETIC,
                                "9")
                        .exports(
                                PackageDesc.of("com.example.api"),
                                ClassFile.ACC_SYNTHETIC,
                                ModuleDesc.of("consumer.zeta"),
                                ModuleDesc.of("consumer.alpha"))
                        .opens(
                                PackageDesc.of("com.example.internal"),
                                ClassFile.ACC_MANDATED,
                                ModuleDesc.of("reflective.consumer"))
                        .uses(ClassDesc.of("missing.Service"))
                        .provides(
                                ClassDesc.of("missing.Service"),
                                ClassDesc.of("impl.Second"),
                                ClassDesc.of("impl.First")));
        return ClassFile.of().buildModule(attribute);
    }

    private TypeModelResult importModule(byte[] bytes) throws IOException {
        Path file = temporaryDirectory.resolve("module-info.class");
        Files.write(file, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource));
    }
}
