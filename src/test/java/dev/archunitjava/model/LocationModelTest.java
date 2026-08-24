package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileOrigin;
import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.ParsedClassFile;
import dev.archunitjava.importer.ParsedLineNumber;
import dev.archunitjava.importer.ParsedMember;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocationModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");

    @TempDir Path temporaryDirectory;

    @Test
    void importsSourceFilesAndMapsBytecodeOffsetsToLines() throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.Located"), builder -> builder
                .with(SourceFileAttribute.of("Located.java"))
                .withMethodBody("run", MethodTypeDesc.of(VOID), ClassFile.ACC_PUBLIC, code -> code
                        .lineNumber(20)
                        .nop()
                        .lineNumber(21)
                        .return_()));

        JavaType type = importType("sample/Located.class", bytes);
        JavaMember method = type.declaredMembers().getFirst();

        assertEquals("Located.java", type.location().sourceFile().orElseThrow().value());
        assertEquals(List.of(new LineNumberEntry(0, 20), new LineNumberEntry(1, 21)),
                method.lineNumbers().entries());
        assertEquals(20, method.bytecodeLocation(0).lineNumber().orElseThrow());
        assertEquals(21, method.bytecodeLocation(1).lineNumber().orElseThrow());
        assertEquals(21, method.bytecodeLocation(100).lineNumber().orElseThrow());
    }

    @Test
    void missingDebugMetadataRemainsAbsent() throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.NoDebug"), builder -> builder
                .withMethodBody("run", MethodTypeDesc.of(VOID), ClassFile.ACC_PUBLIC,
                        code -> code.return_()));

        JavaType type = importType("sample/NoDebug.class", bytes);
        JavaMember method = type.declaredMembers().getFirst();

        assertTrue(type.location().sourceFile().isEmpty());
        assertTrue(method.lineNumbers().entries().isEmpty());
        assertTrue(method.bytecodeLocation(0).lineNumber().isEmpty());
    }

    @Test
    void repeatedOffsetsAndLinesArePreservedAndResolvedDeterministically() {
        ParsedMember method = new ParsedMember(
                ParsedMember.Kind.METHOD,
                "run",
                "()V",
                ClassFile.ACC_PUBLIC,
                true,
                List.of(
                        new ParsedLineNumber(20, 100),
                        new ParsedLineNumber(10, 100),
                        new ParsedLineNumber(0, 5),
                        new ParsedLineNumber(10, 90)));
        ParsedClassFile parsed = new ParsedClassFile(
                "sample.Repeated",
                ClassFile.ACC_PUBLIC,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                "sample/Repeated.class",
                new ClassFileOrigin(
                        ClassFileInput.Kind.DIRECTORY,
                        "C:\\private\\checkout\\classes",
                        "sample/Repeated.class"),
                3,
                Optional.of("C:\\Users\\person\\src\\Repeated.java"),
                List.of(method));

        JavaType type = new TypeModelBuilder()
                .build(new ClassFileReadResult(List.of(parsed), List.of()))
                .types().getFirst();
        JavaMember imported = type.declaredMembers().getFirst();

        assertEquals(
                List.of(
                        new LineNumberEntry(0, 5),
                        new LineNumberEntry(10, 90),
                        new LineNumberEntry(10, 100),
                        new LineNumberEntry(20, 100)),
                imported.lineNumbers().entries());
        assertEquals(90, imported.lineNumbers().lineAt(10).orElseThrow());
        assertEquals(100, imported.lineNumbers().lineAt(20).orElseThrow());
        assertEquals("Repeated.java", type.location().sourceFile().orElseThrow().value());
    }

    @Test
    void publicLocationsKeepLogicalOriginButNeverAbsoluteMachinePaths() {
        ClassResourceLocation location = ClassResourceLocation.from(
                new ClassFileOrigin(
                        ClassFileInput.Kind.JAR,
                        "C:\\Users\\person\\.m2\\library.jar",
                        "a/b/Type.class"),
                4);

        assertEquals(ClassFileInput.Kind.JAR, location.kind());
        assertEquals("library.jar", location.container());
        assertEquals("a/b/Type.class", location.entry());
        assertEquals(4, location.precedence());
        assertFalse(location.toString().contains("Users"));
        assertFalse(location.toString().contains("person"));
    }

    @Test
    void hostileOrPathLikeSourceAttributesAreReducedToSafeLabels() {
        assertEquals("Secret.java", SourceFileName.fromUntrusted(
                "C:\\Users\\person\\src\\Secret.java").orElseThrow().value());
        assertEquals("Type.java", SourceFileName.fromUntrusted("../../src/Type.java").orElseThrow().value());
        assertTrue(SourceFileName.fromUntrusted("../").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new SourceFileName("../Secret.java"));
    }

    @Test
    void invalidOffsetsAndBytecodeRequestsFailLocally() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new LineNumberEntry(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> LineNumberTable.empty().lineAt(-1));

        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.FieldOnly"), builder -> builder
                .withField("value", ClassDesc.ofDescriptor("I"), ClassFile.ACC_PRIVATE));
        JavaMember field = importType("sample/FieldOnly.class", bytes).declaredMembers().getFirst();
        assertThrows(IllegalStateException.class, () -> field.bytecodeLocation(0));
    }

    @Test
    void bytecodeLocationsAttachDirectlyToDependencyEvidence() throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.Evidence"), builder -> builder
                .with(SourceFileAttribute.of("Evidence.java"))
                .withMethodBody("run", MethodTypeDesc.of(VOID), ClassFile.ACC_PUBLIC,
                        code -> code.lineNumber(42).return_()));
        JavaMember method = importType("sample/Evidence.class", bytes).declaredMembers().getFirst();
        MemberId owner = MemberId.of(
                TypeId.ofBinaryName("sample.Evidence"), "run", "()V");

        var evidence = method.bytecodeLocation(0).dependencyEvidence(owner);

        assertEquals(owner, evidence.ownerMember().orElseThrow());
        assertEquals(0, evidence.bytecodeOffset().orElseThrow());
        assertEquals("Evidence.java", evidence.sourceFile().orElseThrow());
        assertEquals(42, evidence.lineNumber().orElseThrow());
        assertTrue(evidence.location().resourcePath().endsWith(
                "/sample/Evidence.class"));
        assertFalse(evidence.location().resourcePath().contains(temporaryDirectory.toString()));
    }

    private JavaType importType(String resourceName, byte[] bytes) throws IOException {
        Path classFile = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource)).types().getFirst();
    }
}
