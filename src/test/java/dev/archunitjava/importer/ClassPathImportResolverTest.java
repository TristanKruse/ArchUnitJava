package dev.archunitjava.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.model.ExternalTypeStub;
import dev.archunitjava.model.JavaTypeKind;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.TypeHierarchy;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassPathImportResolverTest {
    @TempDir Path temporaryDirectory;

    @Test
    void missingTargetsBecomeSeparateDeterministicExternalStubs() throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("missing-target"));
        write(classes, "app/Child.class", classWithSuperclass("app.Child", "missing.Parent"));

        ImportResolutionResult result = new ClassPathImportResolver()
                .resolve(List.of(ClassFileInput.directory(classes)));

        assertEquals(List.of("app.Child"), result.model().types().stream()
                .map(type -> type.binaryName())
                .toList());
        assertTrue(result.model().types().stream()
                .noneMatch(type -> type.binaryName().equals("missing.Parent")));
        ExternalTypeStub missing = result.externalTypes().stream()
                .filter(type -> type.name().binaryName().equals("missing.Parent"))
                .findFirst().orElseThrow();
        assertFalse(missing.hierarchyComplete());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code() == ImportResolutionDiagnosticCode.MISSING_TARGET
                        && value.subject().equals("missing.Parent")));

        TypeHierarchy.Builder hierarchy = TypeHierarchy.builder().addImported(result.model().types());
        result.externalTypes().forEach(hierarchy::addExternal);
        assertFalse(hierarchy.build().transitiveSupertypes("app.Child").complete());
    }

    @Test
    void callerSuppliedExternalFactsRemainExternalAndAvoidMissingDiagnostics() throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("supplied-target"));
        write(classes, "app/Child.class", classWithSuperclass("app.Child", "external.Parent"));
        ExternalTypeStub supplied = ExternalTypeStub.complete(
                "external.Parent", JavaTypeKind.CLASS, null, List.of());

        ImportResolutionResult result = new ClassPathImportResolver().resolve(
                List.of(ClassFileInput.directory(classes)), List.of(supplied));

        assertEquals(supplied, result.externalTypes().stream()
                .filter(value -> value.name().binaryName().equals("external.Parent"))
                .findFirst().orElseThrow());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code() == ImportResolutionDiagnosticCode.EXTERNAL_STUB_USED));
        assertTrue(result.diagnostics().stream().noneMatch(value ->
                value.code() == ImportResolutionDiagnosticCode.MISSING_TARGET
                        && value.subject().equals("external.Parent")));
    }

    @Test
    void duplicateDefinitionsKeepTheFirstWinnerAndConflictEvidence() throws IOException {
        Path first = Files.createDirectory(temporaryDirectory.resolve("first"));
        Path second = Files.createDirectory(temporaryDirectory.resolve("second"));
        write(first, "duplicate/Type.class", classBytes("duplicate.Type", ClassFile.ACC_PUBLIC));
        write(second, "duplicate/Type.class", classBytes(
                "duplicate.Type", ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL));

        ImportResolutionResult result = new ClassPathImportResolver().resolve(List.of(
                ClassFileInput.directory(first), ClassFileInput.directory(second)));

        ResolvedImportedType resolved = result.importedTypes().stream()
                .filter(value -> value.winner().binaryName().equals("duplicate.Type"))
                .findFirst().orElseThrow();
        assertEquals(0, resolved.winner().precedence());
        assertEquals(1, resolved.shadowedDefinitions().size());
        assertEquals(1, resolved.shadowedDefinitions().getFirst().precedence());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code() == ImportResolutionDiagnosticCode.DUPLICATE_DEFINITION
                        && value.subject().equals("duplicate.Type")));
    }

    @Test
    void unsupportedVersionsAreCollectedOrTerminalAccordingToPolicy() throws IOException {
        Path classes = Files.createDirectory(temporaryDirectory.resolve("future"));
        byte[] future = classBytes("future.Type", ClassFile.ACC_PUBLIC);
        int futureVersion = ClassFile.latestMajorVersion() + 1;
        future[6] = (byte) (futureVersion >>> 8);
        future[7] = (byte) futureVersion;
        write(classes, "future/Type.class", future);

        ImportResolutionResult collected = new ClassPathImportResolver()
                .resolve(List.of(ClassFileInput.directory(classes)));
        assertTrue(collected.model().types().isEmpty());
        assertTrue(collected.diagnostics().stream().anyMatch(value ->
                value.code() == ImportResolutionDiagnosticCode.UNSUPPORTED_CLASS_VERSION));

        ClassPathImportResolver strict = new ClassPathImportResolver(
                ClassPathAssemblyOptions.classPathDefaults(),
                ClassFileReaderOptions.defaults(),
                ImportFailurePolicy.strict());
        ImportResolutionException failure = assertThrows(
                ImportResolutionException.class,
                () -> strict.resolve(List.of(ClassFileInput.directory(classes))));
        assertEquals(ImportFailureKind.UNSUPPORTED_CLASS_VERSION, failure.kind());
    }

    @Test
    void anUnsupportedWinnerNeverPromotesAValidShadowDefinition() throws IOException {
        Path first = Files.createDirectory(temporaryDirectory.resolve("unsupported-winner"));
        Path second = Files.createDirectory(temporaryDirectory.resolve("valid-shadow"));
        byte[] future = classBytes("shadowed.Type", ClassFile.ACC_PUBLIC);
        int futureVersion = ClassFile.latestMajorVersion() + 1;
        future[6] = (byte) (futureVersion >>> 8);
        future[7] = (byte) futureVersion;
        write(first, "shadowed/Type.class", future);
        write(second, "shadowed/Type.class", classBytes("shadowed.Type", ClassFile.ACC_PUBLIC));

        ImportResolutionResult result = new ClassPathImportResolver().resolve(List.of(
                ClassFileInput.directory(first), ClassFileInput.directory(second)));

        assertEquals(1, result.assembly().selections().getFirst().shadowedAlternatives().size());
        assertTrue(result.model().types().isEmpty());
        assertTrue(result.importedTypes().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(value ->
                value.code() == ImportResolutionDiagnosticCode.UNSUPPORTED_CLASS_VERSION));
    }

    @Test
    void damagedArchivesAreCollectedOrTerminalAccordingToPolicy() throws IOException {
        Path damaged = Files.writeString(temporaryDirectory.resolve("damaged.jar"), "not a zip");

        ImportResolutionResult collected = new ClassPathImportResolver()
                .resolve(List.of(ClassFileInput.jar(damaged)));
        assertTrue(collected.model().types().isEmpty());
        assertTrue(collected.diagnostics().stream().anyMatch(value ->
                value.code() == ImportResolutionDiagnosticCode.DAMAGED_ARCHIVE));

        ClassPathImportResolver strict = new ClassPathImportResolver(
                ClassPathAssemblyOptions.classPathDefaults(),
                ClassFileReaderOptions.defaults(),
                ImportFailurePolicy.strict());
        ImportResolutionException failure = assertThrows(
                ImportResolutionException.class,
                () -> strict.resolve(List.of(ClassFileInput.jar(damaged))));
        assertEquals(ImportFailureKind.DAMAGED_ARCHIVE, failure.kind());
    }

    private static byte[] classWithSuperclass(String name, String superclass) {
        return ClassFile.of().build(ClassDesc.of(name), builder ->
                builder.withFlags(ClassFile.ACC_PUBLIC)
                        .withSuperclass(ClassDesc.of(superclass)));
    }

    private static byte[] classBytes(String name, int flags) {
        return ClassFile.of().build(ClassDesc.of(name), builder -> builder.withFlags(flags));
    }

    private static void write(Path root, String name, byte[] bytes) throws IOException {
        Path target = root.resolve(name);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
