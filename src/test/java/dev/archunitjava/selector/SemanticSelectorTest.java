package dev.archunitjava.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.AnnotationVisibility;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaModifier;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeKind;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticSelectorTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc MARKER = ClassDesc.of("ann.Marker");
    private static final ClassDesc COMPOSED = ClassDesc.of("ann.Composed");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importModel() throws IOException {
        write("ann/Marker.class", ClassFile.of().build(MARKER, builder -> builder.withFlags(
                ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE
                        | ClassFile.ACC_ABSTRACT | ClassFile.ACC_ANNOTATION)));
        write("ann/Composed.class", ClassFile.of().build(COMPOSED, builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE
                        | ClassFile.ACC_ABSTRACT | ClassFile.ACC_ANNOTATION)
                .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(MARKER)))));
        write("api/Base.class", ClassFile.of().build(ClassDesc.of("api.Base"), builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(MARKER)))));
        write("api/Child.class", ClassFile.of().build(ClassDesc.of("api.Child"), builder -> builder
                .withSuperclass(ClassDesc.of("api.Base"))));
        write("api/MetaSubject.class", ClassFile.of().build(
                ClassDesc.of("api.MetaSubject"), builder -> builder
                        .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(COMPOSED)))));
        TypeAnnotation typeUse = TypeAnnotation.of(
                TypeAnnotation.TargetInfo.ofField(), List.of(), Annotation.of(MARKER));
        write("api/Members.class", ClassFile.of().build(ClassDesc.of("api.Members"), builder -> builder
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL)
                .withField("value", INT, field -> field
                        .withFlags(ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC
                                | ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC)
                        .with(RuntimeVisibleTypeAnnotationsAttribute.of(typeUse)))
                .withMethod("run", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                        method -> method.with(RuntimeInvisibleAnnotationsAttribute.of(
                                Annotation.of(COMPOSED))))));
        write("api/RecordValue.class", ClassFile.of().build(
                ClassDesc.of("api.RecordValue"), builder -> builder
                        .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL)
                        .withSuperclass(ClassDesc.of("java.lang.Record"))
                        .with(RecordAttribute.of())));
        write("api/Sealed.class", ClassFile.of().build(ClassDesc.of("api.Sealed"), builder -> builder
                .with(PermittedSubclassesAttribute.ofSymbols(ClassDesc.of("api.Allowed")))));
        write("api/Allowed.class", ClassFile.of().build(ClassDesc.of("api.Allowed"), builder -> builder
                .withSuperclass(ClassDesc.of("api.Sealed"))));
        write("broken/Unknown.class", ClassFile.of().build(
                ClassDesc.of("broken.Unknown"),
                builder -> builder.withSuperclass(ClassDesc.of("missing.External"))));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void typeModifiersRecordsSealedStateAndVisibilityAreSemanticValues() {
        assertEquals(List.of("api.Members", "api.RecordValue"), names(
                TypeSelector.modifier(JavaModifier.FINAL).selectFrom(model)));
        assertTrue(TypeSelector.visibility(TypeVisibility.PUBLIC).selectFrom(model)
                .selected().stream().anyMatch(type -> type.binaryName().equals("api.Members")));
        assertEquals(List.of("api.RecordValue"), names(TypeSelector.records().selectFrom(model)));
        assertEquals(JavaTypeKind.RECORD,
                TypeSelector.records().selectFrom(model).selected().getFirst().kind());
        assertEquals(List.of("api.Sealed"), names(TypeSelector.sealedTypes().selectFrom(model)));

        assertEquals(List.of("api.Members#valueI"), MemberSelector.modifier(
                JavaMemberModifier.SYNTHETIC).selectFrom(model).selected().stream()
                .map(member -> member.signature().stableKey()).toList());
        assertEquals(1, MemberSelector.visibility(MemberVisibility.PRIVATE)
                .selectFrom(model).selected().size());
    }

    @Test
    void directMetaInheritedAndTypeUseAnnotationModesStayDistinct() {
        AnnotationQuery direct = AnnotationQuery.direct("ann.Marker");
        AnnotationQuery meta = AnnotationQuery.metaAnnotated("ann.Marker");
        AnnotationQuery inherited = AnnotationQuery.inherited("ann.Marker");
        AnnotationQuery typeUse = AnnotationQuery.typeUse("ann.Marker");

        assertEquals(List.of("ann.Composed", "api.Base"), names(
                TypeSelector.annotatedWith(direct).selectFrom(model)));
        assertEquals(List.of("api.MetaSubject"), names(
                TypeSelector.annotatedWith(meta).selectFrom(model)));
        assertEquals(List.of("api.Child"), names(
                TypeSelector.annotatedWith(inherited).selectFrom(model)));
        assertEquals(List.of("api.Members#valueI"), MemberSelector.annotatedWith(typeUse)
                .selectFrom(model).selected().stream()
                .map(member -> member.signature().stableKey()).toList());
        assertEquals(List.of("api.Members#run()V"), MemberSelector.annotatedWith(
                        AnnotationQuery.metaAnnotated("ann.Marker")
                                .withVisibility(AnnotationVisibility.RUNTIME_INVISIBLE))
                .selectFrom(model).selected().stream()
                .map(member -> member.signature().stableKey()).toList());
        assertThrows(IllegalArgumentException.class, () -> MemberSelector.annotatedWith(inherited));
    }

    @Test
    void unknownHierarchyEvidenceFollowsTheSelectedPolicy() {
        TypeSelector exclude = TypeSelector.assignableTo(
                "unrelated.Target", UnknownHierarchyPolicy.EXCLUDE);
        TypeSelector include = TypeSelector.assignableTo(
                "unrelated.Target", UnknownHierarchyPolicy.INCLUDE);
        TypeSelector fail = TypeSelector.assignableTo(
                "unrelated.Target", UnknownHierarchyPolicy.FAIL);

        TypeSelection excluded = exclude.selectFrom(model.types().stream()
                .filter(type -> type.binaryName().equals("broken.Unknown")).toList());
        TypeSelection included = include.selectFrom(model.types().stream()
                .filter(type -> type.binaryName().equals("broken.Unknown")).toList());

        assertTrue(excluded.selected().isEmpty());
        assertEquals(List.of("broken.Unknown"), names(included));
        assertEquals(SelectionDiagnosticCode.UNKNOWN_ASSIGNABILITY,
                included.selectionDiagnostics().getFirst().code());
        assertThrows(IncompleteSelectionException.class, () -> fail.selectFrom(
                model.types().stream().filter(type ->
                        type.binaryName().equals("broken.Unknown")).toList()));
        assertEquals(List.of("api.Child"), names(TypeSelector.assignableTo(
                "api.Base", UnknownHierarchyPolicy.FAIL).selectFrom(model.types().stream()
                .filter(type -> type.binaryName().equals("api.Child")).toList())));
    }

    private static List<String> names(TypeSelection selection) {
        return selection.selected().stream().map(JavaType::binaryName).sorted().toList();
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
