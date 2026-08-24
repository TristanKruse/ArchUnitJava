package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileDiagnosticCode;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileOrigin;
import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.ClassFileResource;
import dev.archunitjava.importer.ParsedClassFile;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeModelBuilderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void mapsClassKindModifiersOwnershipVersionAndOrigin() {
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER;

        JavaType type = build(parsed("example.domain.Service", flags)).types().getFirst();

        assertEquals(JavaTypeKind.CLASS, type.kind());
        assertEquals(Set.of(JavaModifier.PUBLIC, JavaModifier.FINAL), type.modifiers());
        assertEquals("example.domain.Service", type.binaryName());
        assertEquals("example.domain.Service", type.sourceName());
        assertEquals("Service", type.name().simpleName());
        assertEquals(new TypeOwner("example.domain"), type.owner());
        assertEquals(new ClassFileVersion(ClassFile.JAVA_25_VERSION, 0), type.classFileVersion());
        assertEquals(origin("example/domain/Service.class"), type.origin());
        assertEquals(flags, type.accessFlags());
        assertEquals(0, type.unrecognizedAccessFlags());
    }

    @Test
    void mapsInterfacesEnumsAndAnnotationsWithSpecificKinds() {
        List<ParsedClassFile> parsed = List.of(
                parsed("types.Api", ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT),
                parsed("types.Mode", ClassFile.ACC_PUBLIC | ClassFile.ACC_ENUM | ClassFile.ACC_FINAL),
                parsed("types.Marker", ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE
                        | ClassFile.ACC_ABSTRACT | ClassFile.ACC_ANNOTATION));
        ClassFileReadResult read = readResult(parsed);

        TypeModelResult result = new TypeModelBuilder().build(read);

        assertEquals(
                List.of(JavaTypeKind.INTERFACE, JavaTypeKind.ANNOTATION, JavaTypeKind.ENUM),
                result.types().stream().map(JavaType::kind).toList());
    }

    @Test
    void integratesSuccessfulJdkParsingWithTheTypeModel() throws IOException {
        byte[] bytes = classBytes(
                "integration.Api", AccessFlag.PUBLIC, AccessFlag.INTERFACE, AccessFlag.ABSTRACT);
        ClassFileReadResult read = new ClassFileReader().read(resource("integration/Api.class", bytes));

        TypeModelResult result = new TypeModelBuilder().build(read);

        assertEquals(JavaTypeKind.INTERFACE, result.types().getFirst().kind());
        assertEquals("integration.Api", result.types().getFirst().binaryName());
        assertTrue(result.classFileDiagnostics().isEmpty());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void rawAndUnrecognizedAccessFlagBitsAreNeverLost() {
        int unexpectedClassBits = ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_NATIVE;
        int raw = ClassFile.ACC_PUBLIC | unexpectedClassBits;

        JavaType type = build(parsed("odd.Flags", raw)).types().getFirst();

        assertEquals(raw, type.accessFlags());
        assertEquals(unexpectedClassBits, type.unrecognizedAccessFlags());
        assertEquals(Set.of(JavaModifier.PUBLIC), type.modifiers());
    }

    @Test
    void dollarSignsRemainCanonicalUntilNestedTypeMetadataIsAvailable() {
        JavaType type = build(parsed("legal.Top$Level", ClassFile.ACC_PUBLIC)).types().getFirst();

        assertEquals("legal.Top$Level", type.binaryName());
        assertEquals("legal.Top$Level", type.sourceName());
        assertEquals("Top$Level", type.name().simpleName());
    }

    @Test
    void unnamedPackageHasExplicitTopLevelOwnership() {
        JavaType type = build(parsed("Standalone", 0)).types().getFirst();

        assertEquals(new TypeOwner(""), type.owner());
    }

    @Test
    void moduleDescriptorsAreDiagnosedInsteadOfPretendingToBeTypes() {
        ParsedClassFile module = new ParsedClassFile(
                "module-info", ClassFile.ACC_MODULE, ClassFile.JAVA_25_VERSION, 0, true,
                "module-info.class", origin("module-info.class"), 0);

        TypeModelResult result = build(module);

        assertTrue(result.types().isEmpty());
        assertEquals(TypeModelDiagnosticCode.MODULE_DESCRIPTOR_IS_NOT_A_TYPE,
                result.diagnostics().getFirst().code());
    }

    @Test
    void invalidBinaryNamesAreLocalTypedDiagnostics() {
        ParsedClassFile invalid = new ParsedClassFile(
                "bad/name", 0, ClassFile.JAVA_25_VERSION, 0, false,
                "bad/name.class", origin("bad/name.class"), 0);

        TypeModelResult result = build(invalid);

        assertTrue(result.types().isEmpty());
        assertEquals(TypeModelDiagnosticCode.INVALID_BINARY_NAME, result.diagnostics().getFirst().code());
    }

    @Test
    void malformedAndUnsupportedVersionDiagnosticsSurviveTheModelBoundary() throws IOException {
        byte[] malformed = new byte[] {1, 2, 3};
        byte[] future = classBytes("future.Type", AccessFlag.PUBLIC);
        int futureVersion = ClassFile.latestMajorVersion() + 1;
        future[6] = (byte) (futureVersion >>> 8);
        future[7] = (byte) futureVersion;
        ClassFileReadResult read = new ClassFileReader().readAll(List.of(
                resource("broken/Type.class", malformed), resource("future/Type.class", future)));

        TypeModelResult result = new TypeModelBuilder().build(read);

        assertEquals(
                List.of(ClassFileDiagnosticCode.MALFORMED_CLASS_FILE,
                        ClassFileDiagnosticCode.UNSUPPORTED_CLASS_VERSION),
                result.classFileDiagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
        assertTrue(result.types().isEmpty());
    }

    @Test
    void outputOrderingAndCollectionsAreImmutable() {
        ClassFileReadResult read = readResult(List.of(
                parsed("z.Last", ClassFile.ACC_PUBLIC), parsed("a.First", ClassFile.ACC_PUBLIC)));

        TypeModelResult result = new TypeModelBuilder().build(read);

        assertEquals(List.of("a.First", "z.Last"),
                result.types().stream().map(JavaType::binaryName).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.types().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.types().getFirst().modifiers().clear());
    }

    private static TypeModelResult build(ParsedClassFile parsed) {
        return new TypeModelBuilder().build(readResult(List.of(parsed)));
    }

    private static ParsedClassFile parsed(String binaryName, int accessFlags) {
        String resourceName = binaryName.replace('.', '/') + ".class";
        return new ParsedClassFile(
                binaryName,
                accessFlags,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                resourceName,
                origin(resourceName),
                0);
    }

    private static ClassFileReadResult readResult(List<ParsedClassFile> parsed) {
        return new ClassFileReadResult(parsed, List.of());
    }

    private static byte[] classBytes(String binaryName, AccessFlag... flags) {
        return ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder.withFlags(flags));
    }

    private ClassFileResource resource(String name, byte[] bytes) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources().stream()
                .filter(resource -> resource.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ClassFileOrigin origin(String name) {
        return new ClassFileOrigin(ClassFileInput.Kind.DIRECTORY, "test-classes", name);
    }
}
