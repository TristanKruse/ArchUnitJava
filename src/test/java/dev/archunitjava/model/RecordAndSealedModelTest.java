package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileOrigin;
import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.ParsedClassFile;
import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordAndSealedModelTest {
    @TempDir Path temporaryDirectory;

    @Test
    void recordComponentsRetainDescriptorGenericSignatureAndAnnotations() throws IOException {
        String signature = "Ljava/util/List<Ljava/lang/String;>;";
        RecordComponentInfo component = RecordComponentInfo.of(
                "names",
                ClassDesc.of("java.util.List"),
                SignatureAttribute.of(Signature.parseFrom(signature)),
                RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("ann.Marker"))));
        write("sample/Names.class", ClassFile.of().build(ClassDesc.of("sample.Names"), builder -> builder
                .withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL)
                .withSuperclass(ClassDesc.of("java.lang.Record"))
                .with(RecordAttribute.of(component))));

        JavaType type = importTypes().getFirst();
        JavaRecordComponent names = type.recordComponents().getFirst();

        assertEquals(JavaTypeKind.RECORD, type.kind());
        assertEquals("names", names.name());
        assertEquals("Ljava/util/List;", names.descriptor());
        assertEquals(signature, names.typeView().declaredSignature().orElseThrow());
        assertEquals(List.of("ann.Marker"), names.annotations().stream()
                .map(value -> value.annotation().type().binaryName()).toList());
        assertEquals(AnnotationSiteKind.RECORD_COMPONENT,
                names.annotations().getFirst().site().kind());
    }

    @Test
    void permittedSubclassesRemainDistinctFromObservedDirectSubclasses() throws IOException {
        write("sample/Parent.class", ClassFile.of().build(ClassDesc.of("sample.Parent"), builder -> builder
                .withFlags(AccessFlag.PUBLIC)
                .with(PermittedSubclassesAttribute.ofSymbols(
                        ClassDesc.of("sample.Child"), ClassDesc.of("sample.Missing")))));
        write("sample/Child.class", ClassFile.of().build(ClassDesc.of("sample.Child"), builder -> builder
                .withSuperclass(ClassDesc.of("sample.Parent"))));
        write("sample/Rogue.class", ClassFile.of().build(ClassDesc.of("sample.Rogue"), builder -> builder
                .withSuperclass(ClassDesc.of("sample.Parent"))));

        List<JavaType> types = importTypes();
        JavaType parent = types.stream()
                .filter(type -> type.binaryName().equals("sample.Parent"))
                .findFirst().orElseThrow();
        SealedHierarchyResult result = TypeHierarchy.of(types).sealedHierarchy("sample.Parent");

        assertTrue(parent.isSealed());
        assertEquals(List.of("sample.Child", "sample.Missing"), names(parent.permittedSubclasses()));
        assertEquals(List.of("sample.Child", "sample.Missing"), typeNames(
                result.declaredPermittedSubclasses()));
        assertEquals(List.of("sample.Child", "sample.Rogue"), typeNames(
                result.observedDirectSubclasses()));
        assertEquals(List.of("sample.Missing"), typeNames(result.missingPermittedSubclasses()));
        assertEquals(
                List.of(
                        SealedHierarchyDiagnosticCode.MISSING_PERMITTED_SUBCLASS,
                        SealedHierarchyDiagnosticCode.OBSERVED_SUBCLASS_NOT_PERMITTED),
                result.diagnostics().stream().map(SealedHierarchyDiagnostic::code).toList());
        assertFalse(result.complete());
    }

    @Test
    void malformedSealedMetadataIsBoundedAndQueryable() {
        JavaType malformed = build(new ParsedClassFile(
                "sample.Broken",
                ClassFile.ACC_PUBLIC,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                "sample/Broken.class",
                origin("sample/Broken.class"),
                0,
                Optional.of("java.lang.Object"),
                List.of(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                false,
                List.of(),
                true,
                List.of("sample.Broken", "sample.Broken")));

        SealedHierarchyResult result = TypeHierarchy.of(List.of(malformed))
                .sealedHierarchy("sample.Broken");

        assertEquals(2, result.declaredPermittedSubclasses().size());
        assertEquals(
                List.of(
                        SealedHierarchyDiagnosticCode.DUPLICATE_PERMITTED_SUBCLASS,
                        SealedHierarchyDiagnosticCode.SELF_PERMITTED_SUBCLASS),
                result.diagnostics().stream().map(SealedHierarchyDiagnostic::code).toList());
        assertFalse(result.complete());
    }

    @Test
    void emptyAndMissingSealedQueriesReturnExplicitDiagnostics() {
        JavaType empty = build(new ParsedClassFile(
                "sample.Empty",
                ClassFile.ACC_PUBLIC,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                "sample/Empty.class",
                origin("sample/Empty.class"),
                0,
                Optional.of("java.lang.Object"),
                List.of(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                false,
                List.of(),
                true,
                List.of()));
        TypeHierarchy hierarchy = TypeHierarchy.of(List.of(empty));

        assertEquals(SealedHierarchyDiagnosticCode.EMPTY_PERMITTED_SUBCLASS_LIST,
                hierarchy.sealedHierarchy("sample.Empty").diagnostics().getFirst().code());
        assertEquals(SealedHierarchyDiagnosticCode.QUERY_TYPE_MISSING,
                hierarchy.sealedHierarchy("missing.Type").diagnostics().getFirst().code());
    }

    private void write(String resource, byte[] bytes) throws IOException {
        Path file = temporaryDirectory.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private List<JavaType> importTypes() {
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        return new TypeModelBuilder().build(new ClassFileReader().readAll(resources)).types();
    }

    private static JavaType build(ParsedClassFile parsed) {
        return new TypeModelBuilder().build(new ClassFileReadResult(List.of(parsed), List.of()))
                .types().getFirst();
    }

    private static ClassFileOrigin origin(String resource) {
        return new ClassFileOrigin(ClassFileInput.Kind.DIRECTORY, "test-classes", resource);
    }

    private static List<String> names(List<JvmReferenceType> types) {
        return types.stream().map(JvmReferenceType::binaryName).toList();
    }

    private static List<String> typeNames(List<JavaTypeName> types) {
        return types.stream().map(JavaTypeName::binaryName).toList();
    }
}
