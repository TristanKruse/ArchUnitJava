package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileOrigin;
import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.ParsedClassFile;
import dev.archunitjava.importer.ParsedMember;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenericSignatureModelTest {
    @TempDir Path temporaryDirectory;

    @Test
    void parsesParameterizedTypesWildcardsArraysAndNestedOwners() {
        GenericType.ClassType map = assertInstanceOf(
                GenericType.ClassType.class,
                GenericSignatures.parseField(
                        "Ljava/util/Map<Ljava/lang/String;Ljava/util/List<+Ljava/lang/Number;>;>;"));
        GenericType.ClassType list = assertInstanceOf(
                GenericType.ClassType.class,
                map.typeArguments().get(1).type().orElseThrow());

        assertEquals("java.util.Map", map.rawType().binaryName());
        assertEquals(GenericTypeArgument.Variance.EXTENDS,
                list.typeArguments().getFirst().variance());
        assertEquals(
                List.of("java.lang.Number", "java.lang.String", "java.util.List", "java.util.Map"),
                map.referencedTypes().stream().map(JvmReferenceType::binaryName).toList());

        GenericType.ArrayType array = assertInstanceOf(
                GenericType.ArrayType.class,
                GenericSignatures.parseField("[[TT;"));
        assertEquals(2, array.dimensions());
        assertEquals("T", assertInstanceOf(
                GenericType.TypeVariable.class, array.elementType()).name());

        GenericType.ClassType nested = assertInstanceOf(
                GenericType.ClassType.class,
                GenericSignatures.parseField(
                        "Lsample/Outer<TT;>.Inner<Ljava/lang/String;>;"));
        assertEquals("sample.Outer$Inner", nested.rawType().binaryName());
        assertEquals("sample.Outer", nested.owner().orElseThrow().rawType().binaryName());
        assertEquals(1, nested.owner().orElseThrow().typeArguments().size());
    }

    @Test
    void parsesClassTypeParametersAndMultipleBounds() {
        GenericClassSignature signature = GenericSignatures.parseClass(
                "<T:Ljava/lang/Number;:Ljava/lang/Comparable<TT;>;>"
                        + "Ljava/lang/Object;Ljava/util/function/Supplier<TT;>;" );
        GenericTypeParameter parameter = signature.typeParameters().getFirst();

        assertEquals("T", parameter.name());
        assertEquals("java.lang.Number", assertInstanceOf(
                GenericType.ClassType.class, parameter.classBound().orElseThrow())
                .rawType().binaryName());
        assertEquals("java.lang.Comparable", assertInstanceOf(
                GenericType.ClassType.class, parameter.interfaceBounds().getFirst())
                .rawType().binaryName());
        assertEquals("java.util.function.Supplier", signature.interfaces().getFirst()
                .rawType().binaryName());
    }

    @Test
    void parsesGenericMethodsIncludingThrowsAndSuperWildcards() {
        GenericMethodSignature signature = GenericSignatures.parseMethod(
                "<T:Ljava/lang/Object;>(Ljava/util/List<-TT;>;[TT;)TT;"
                        + "^Ljava/io/IOException;^TT;");
        GenericType.ClassType list = assertInstanceOf(
                GenericType.ClassType.class, signature.parameterTypes().getFirst());

        assertEquals(GenericTypeArgument.Variance.SUPER,
                list.typeArguments().getFirst().variance());
        assertInstanceOf(GenericType.ArrayType.class, signature.parameterTypes().get(1));
        assertInstanceOf(GenericType.TypeVariable.class, signature.returnType());
        assertEquals(2, signature.throwsTypes().size());
        assertEquals(List.of("java.io.IOException", "java.lang.Object", "java.util.List"),
                signature.referencedTypes().stream().map(JvmReferenceType::binaryName).toList());
    }

    @Test
    void extractsSignatureAttributesWithoutReplacingErasedDescriptors() throws IOException {
        String classSignature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";
        String fieldSignature = "Ljava/util/List<Ljava/lang/String;>;";
        String methodSignature = "<T:Ljava/lang/Object;>(TT;)TT;";
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.Generic"), builder -> builder
                .with(SignatureAttribute.of(ClassSignature.parseFrom(classSignature)))
                .withField("names", ClassDesc.of("java.util.List"), field -> field
                        .with(SignatureAttribute.of(Signature.parseFrom(fieldSignature))))
                .withMethod("identity",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Object"),
                                ClassDesc.of("java.lang.Object")),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                        method -> method.with(SignatureAttribute.of(
                                MethodSignature.parseFrom(methodSignature)))));

        Path classFile = temporaryDirectory.resolve("sample/Generic.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources().getFirst();
        var read = new ClassFileReader().read(resource);
        JavaType type = new TypeModelBuilder().build(read).types().getFirst();

        assertEquals(Optional.of(classSignature), read.parsedClass().orElseThrow().genericSignature());
        assertEquals(classSignature, type.genericView().declaredSignature().orElseThrow());
        assertFalse(type.genericView().usesErasedFallback());
        JavaMember field = type.declaredMembers().stream()
                .filter(member -> member.name().equals("names")).findFirst().orElseThrow();
        JavaMember method = type.declaredMembers().stream()
                .filter(member -> member.name().equals("identity")).findFirst().orElseThrow();
        assertEquals("Ljava/util/List;", field.fieldType().descriptor());
        assertEquals(fieldSignature, field.genericFieldView().declaredSignature().orElseThrow());
        assertEquals(List.of("java.util.List"), names(field.genericFieldView()
                .referencedTypes(TypeReferenceEvidence.ERASED)));
        assertEquals(List.of("java.lang.String", "java.util.List"), names(field.genericFieldView()
                .referencedTypes(TypeReferenceEvidence.GENERIC)));
        assertEquals(List.of("java.lang.String", "java.util.List"), names(field.genericFieldView()
                .referencedTypes(TypeReferenceEvidence.COMBINED)));
        assertEquals("(Ljava/lang/Object;)Ljava/lang/Object;", method.methodType().descriptor());
        assertEquals(methodSignature, method.genericMethodView().declaredSignature().orElseThrow());
    }

    @Test
    void absentSignaturesUseErasedViewsWithoutDiagnostics() {
        JavaType type = build(parsed(Optional.empty(), Optional.empty()));

        assertTrue(type.genericView().usesErasedFallback());
        assertTrue(type.genericView().diagnostics().isEmpty());
        assertTrue(type.declaredMembers().getFirst().genericFieldView().usesErasedFallback());
        assertTrue(type.declaredMembers().getFirst().genericFieldView().diagnostics().isEmpty());
    }

    @Test
    void malformedSignaturesFallBackToErasedTypesWithDiagnostics() {
        String badClass = "<T:Ljava/lang/Object;>Ljava/lang/Object";
        String badField = "Ljava/util/List<Ljava/lang/String;>";
        JavaType type = build(parsed(Optional.of(badClass), Optional.of(badField)));
        GenericSignatureDiagnostic classDiagnostic = type.genericView().diagnostics().getFirst();
        GenericFieldView field = type.declaredMembers().getFirst().genericFieldView();

        assertTrue(type.genericView().usesErasedFallback());
        assertEquals(badClass, classDiagnostic.signature());
        assertTrue(classDiagnostic.errorOffset() > 0);
        assertEquals("java.lang.Object", type.superclass().orElseThrow().binaryName());
        assertEquals("java.util.List", assertInstanceOf(
                JvmReferenceType.class, field.erasedType()).binaryName());
        assertEquals(badField, field.diagnostics().getFirst().signature());
        assertTrue(field.referencedTypes(TypeReferenceEvidence.GENERIC).isEmpty());
        assertEquals(List.of("java.util.List"), names(
                field.referencedTypes(TypeReferenceEvidence.COMBINED)));
    }

    @Test
    void malformedAndExcessivelyNestedSignaturesAreRejectedDeterministically() {
        InvalidGenericSignatureException malformed = assertThrows(
                InvalidGenericSignatureException.class,
                () -> GenericSignatures.parseMethod("(Ljava/lang/String;)"));
        InvalidGenericSignatureException excessiveArray = assertThrows(
                InvalidGenericSignatureException.class,
                () -> GenericSignatures.parseField("[".repeat(256) + "Ljava/lang/String;"));

        assertEquals("Expected a type", malformed.reason());
        assertEquals(255, excessiveArray.errorOffset());
    }

    private static ParsedClassFile parsed(
            Optional<String> classSignature, Optional<String> fieldSignature) {
        return new ParsedClassFile(
                "sample.Generic",
                ClassFile.ACC_PUBLIC,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                "sample/Generic.class",
                new ClassFileOrigin(
                        ClassFileInput.Kind.DIRECTORY, "test-classes", "sample/Generic.class"),
                0,
                Optional.of("java.lang.Object"),
                List.of(),
                Optional.empty(),
                List.of(new ParsedMember(
                        ParsedMember.Kind.FIELD,
                        "names",
                        "Ljava/util/List;",
                        ClassFile.ACC_PRIVATE,
                        false,
                        List.of(),
                        fieldSignature)),
                List.of(),
                List.of(),
                classSignature);
    }

    private static JavaType build(ParsedClassFile parsed) {
        return new TypeModelBuilder()
                .build(new ClassFileReadResult(List.of(parsed), List.of()))
                .types().getFirst();
    }

    private static List<String> names(List<JvmReferenceType> types) {
        return types.stream().map(JvmReferenceType::binaryName).toList();
    }
}
