package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.AnnotationDefaultAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnnotationModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc STRING = ClassDesc.of("java.lang.String");
    private static final ClassDesc MARKER = ClassDesc.of("ann.Marker");

    @TempDir Path temporaryDirectory;

    @Test
    void annotationValuesAreLosslessAndVisibilityIsPreserved() throws IOException {
        Annotation nested = Annotation.of(
                ClassDesc.of("ann.Nested"), AnnotationElement.ofString("name", "inside"));
        Annotation complex = Annotation.of(
                ClassDesc.of("ann.Complex"),
                AnnotationElement.ofBoolean("bool", true),
                AnnotationElement.ofByte("byteValue", (byte) -2),
                AnnotationElement.ofChar("charValue", '\u20ac'),
                AnnotationElement.ofShort("shortValue", (short) 7),
                AnnotationElement.ofInt("intValue", 42),
                AnnotationElement.ofLong("longValue", 9L),
                AnnotationElement.ofFloat("floatValue", -0.0f),
                AnnotationElement.ofDouble("doubleValue", -0.0d),
                AnnotationElement.ofString("text", "hello"),
                AnnotationElement.of(
                        "mode", AnnotationValue.ofEnum(ClassDesc.of("ann.Mode"), "FAST")),
                AnnotationElement.ofClass("type", ClassDesc.ofDescriptor("[Ljava/lang/String;")),
                AnnotationElement.ofAnnotation("nested", nested),
                AnnotationElement.ofArray(
                        "values", AnnotationValue.ofInt(1), AnnotationValue.ofString("two")));
        write("sample/Annotated.class", classBytes("sample.Annotated", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(complex))
                .with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(MARKER)))));

        JavaType type = importTypes().getFirst();
        JavaAnnotationOccurrence occurrence = type.annotations().stream()
                .filter(value -> value.annotation().type().binaryName().equals("ann.Complex"))
                .findFirst().orElseThrow();
        Map<String, JavaAnnotationValue> values = occurrence.annotation().elements().stream()
                .collect(Collectors.toMap(JavaAnnotationElement::name, JavaAnnotationElement::value));

        assertEquals(AnnotationVisibility.RUNTIME_VISIBLE, occurrence.visibility());
        assertEquals(AnnotationSiteKind.TYPE_DECLARATION, occurrence.site().kind());
        assertEquals(new JavaAnnotationValue.ScalarValue(
                        JavaAnnotationValue.ScalarKind.BOOLEAN, "true"),
                values.get("bool"));
        assertEquals(Integer.toString('\u20ac'),
                assertInstanceOf(JavaAnnotationValue.ScalarValue.class, values.get("charValue"))
                        .encodedValue());
        assertEquals(Integer.toUnsignedString(Float.floatToRawIntBits(-0.0f)),
                assertInstanceOf(JavaAnnotationValue.ScalarValue.class, values.get("floatValue"))
                        .encodedValue());
        assertEquals(Long.toUnsignedString(Double.doubleToRawLongBits(-0.0d)),
                assertInstanceOf(JavaAnnotationValue.ScalarValue.class, values.get("doubleValue"))
                        .encodedValue());
        assertEquals(new JvmReferenceType("ann.Mode"),
                assertInstanceOf(JavaAnnotationValue.EnumValue.class, values.get("mode")).enumType());
        assertEquals("[Ljava/lang/String;",
                assertInstanceOf(JavaAnnotationValue.ClassValue.class, values.get("type")).descriptor());
        assertEquals("ann.Nested", assertInstanceOf(
                        JavaAnnotationValue.NestedAnnotationValue.class, values.get("nested"))
                .annotation().type().binaryName());
        assertEquals(2, assertInstanceOf(
                        JavaAnnotationValue.ArrayValue.class, values.get("values"))
                .values().size());
        assertTrue(type.annotations().stream().anyMatch(value ->
                value.visibility() == AnnotationVisibility.RUNTIME_INVISIBLE));
    }

    @Test
    void declarationParameterRecordAndTypeUseSitesRemainDistinct() throws IOException {
        Annotation marker = Annotation.of(MARKER);
        TypeAnnotation fieldTypeAnnotation = TypeAnnotation.of(
                TypeAnnotation.TargetInfo.ofField(),
                List.of(TypeAnnotation.TypePathComponent.ARRAY),
                marker);
        write("sample/Sites.class", classBytes("sample.Sites", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(marker))
                .withField("names", ClassDesc.ofDescriptor("[Ljava/lang/String;"), field -> field
                        .with(RuntimeVisibleAnnotationsAttribute.of(marker))
                        .with(RuntimeInvisibleTypeAnnotationsAttribute.of(fieldTypeAnnotation)))
                .withMethod("call", MethodTypeDesc.of(VOID, INT), ClassFile.ACC_PUBLIC,
                        method -> method.with(RuntimeVisibleParameterAnnotationsAttribute.of(
                                List.of(List.of(marker)))))
                .withMethod("<init>", MethodTypeDesc.of(VOID), ClassFile.ACC_PUBLIC,
                        method -> method
                                .with(RuntimeVisibleAnnotationsAttribute.of(marker))
                                .withCode(code -> code.return_()))
                .with(RecordAttribute.of(RecordComponentInfo.of(
                        "component", STRING, RuntimeVisibleAnnotationsAttribute.of(marker))))));

        JavaType type = importTypes().getFirst();
        List<AnnotationSiteKind> typeSites = type.annotations().stream()
                .map(value -> value.site().kind()).distinct().sorted().toList();
        List<AnnotationSiteKind> memberSites = type.declaredMembers().stream()
                .flatMap(member -> member.annotations().stream())
                .map(value -> value.site().kind()).distinct().sorted().toList();

        assertEquals(
                List.of(AnnotationSiteKind.TYPE_DECLARATION, AnnotationSiteKind.RECORD_COMPONENT),
                typeSites);
        assertEquals(
                List.of(
                        AnnotationSiteKind.FIELD_DECLARATION,
                        AnnotationSiteKind.CONSTRUCTOR_DECLARATION,
                        AnnotationSiteKind.PARAMETER,
                        AnnotationSiteKind.TYPE_USE),
                memberSites);
        JavaAnnotationOccurrence parameter = type.declaredMembers().stream()
                .flatMap(member -> member.annotations().stream())
                .filter(value -> value.site().kind() == AnnotationSiteKind.PARAMETER)
                .findFirst().orElseThrow();
        assertEquals(0, parameter.site().parameterIndex().orElseThrow());
        JavaAnnotationOccurrence typeUse = type.declaredMembers().stream()
                .flatMap(member -> member.annotations().stream())
                .filter(value -> value.site().kind() == AnnotationSiteKind.TYPE_USE)
                .findFirst().orElseThrow();
        assertEquals("FIELD", typeUse.site().typeUseTarget().orElseThrow().targetType());
        assertEquals(List.of("ARRAY:0"), typeUse.site().typeUseTarget().orElseThrow().path());
    }

    @Test
    void annotationMethodDefaultsRemainSeparateFromExplicitValues() throws IOException {
        write("ann/Config.class", annotationClass("ann.Config", builder -> builder
                .withMethod("value", MethodTypeDesc.of(STRING),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                        method -> method.with(AnnotationDefaultAttribute.of(
                                AnnotationValue.ofString("fallback"))))));

        JavaMember value = importTypes().getFirst().declaredMembers().getFirst();

        assertEquals(new JavaAnnotationValue.ScalarValue(
                        JavaAnnotationValue.ScalarKind.STRING, "fallback"),
                value.annotationDefault().orElseThrow());
    }

    @Test
    void repeatedOccurrencesAreSortedButNeverSilentlyDeduplicated() throws IOException {
        Annotation marker = Annotation.of(MARKER);
        write("sample/Repeated.class", classBytes("sample.Repeated", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(marker, marker))));

        JavaType type = importTypes().getFirst();

        assertEquals(2, type.annotations().size());
        assertEquals(type.annotations().get(0), type.annotations().get(1));
    }

    @Test
    void metaAnnotationTraversalIsBoundedAndReportsMissingTypes() throws IOException {
        write("ann/A.class", annotationClass("ann.A", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(
                        Annotation.of(ClassDesc.of("ann.B"))))));
        write("ann/B.class", annotationClass("ann.B", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(
                        Annotation.of(ClassDesc.of("ann.Missing"))))));
        MetaAnnotationResolver resolver = new MetaAnnotationResolver(importTypes());

        MetaAnnotationResult completeDepth = resolver.resolve("ann.A", 5);
        MetaAnnotationResult bounded = resolver.resolve("ann.A", 1);

        assertEquals(List.of(new JvmReferenceType("ann.B"), new JvmReferenceType("ann.Missing")),
                completeDepth.annotations());
        assertEquals(List.of(new JvmReferenceType("ann.Missing")),
                completeDepth.missingAnnotationTypes());
        assertFalse(completeDepth.complete());
        assertEquals(List.of(new JvmReferenceType("ann.B")), bounded.annotations());
        assertTrue(bounded.depthLimitReached());
        assertTrue(bounded.missingAnnotationTypes().isEmpty());
    }

    @Test
    void metaAnnotationCyclesTerminateExplicitly() throws IOException {
        write("ann/A.class", annotationClass("ann.A", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("ann.B"))))));
        write("ann/B.class", annotationClass("ann.B", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("ann.A"))))));

        MetaAnnotationResult result = new MetaAnnotationResolver(importTypes()).resolve("ann.A", 10);

        assertTrue(result.cycleDetected());
        assertEquals(List.of(new JvmReferenceType("ann.B")), result.annotations());
    }

    private byte[] annotationClass(String binaryName, Consumer<java.lang.classfile.ClassBuilder> customizer) {
        return classBytes(binaryName, builder -> {
            builder.withFlags(
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE
                            | ClassFile.ACC_ABSTRACT | ClassFile.ACC_ANNOTATION);
            customizer.accept(builder);
        });
    }

    private byte[] classBytes(String binaryName, Consumer<java.lang.classfile.ClassBuilder> customizer) {
        return ClassFile.of().build(ClassDesc.of(binaryName), customizer);
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
}
