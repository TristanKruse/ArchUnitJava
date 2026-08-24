package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeHierarchyTest {
    @TempDir Path temporaryDirectory;

    @Test
    void importsSuperclassAndInterfacesWithoutClassLoading() throws IOException {
        write("api/Parent.class", classBytes("api.Parent", builder -> builder
                .withFlags(AccessFlag.PUBLIC, AccessFlag.INTERFACE, AccessFlag.ABSTRACT)));
        write("api/ChildApi.class", classBytes("api.ChildApi", builder -> builder
                .withFlags(AccessFlag.PUBLIC, AccessFlag.INTERFACE, AccessFlag.ABSTRACT)
                .withInterfaceSymbols(ClassDesc.of("api.Parent"))));
        write("impl/Base.class", classBytes("impl.Base", builder -> builder.withFlags(AccessFlag.PUBLIC)));
        write("impl/Child.class", classBytes("impl.Child", builder -> builder
                .withFlags(AccessFlag.PUBLIC)
                .withSuperclass(ClassDesc.of("impl.Base"))
                .withInterfaceSymbols(ClassDesc.of("api.ChildApi"))));

        List<JavaType> types = importTypes();
        TypeHierarchy hierarchy = TypeHierarchy.builder()
                .addImported(types)
                .addExternal(ExternalTypeStub.complete(
                        "java.lang.Object", JavaTypeKind.CLASS, null, List.of()))
                .build();

        assertEquals(List.of("impl.Base"), types.stream()
                .filter(type -> type.binaryName().equals("impl.Child"))
                .findFirst().orElseThrow().superclass().stream()
                .map(JvmReferenceType::binaryName).toList());
        assertEquals(HierarchyRelationshipKind.IMPLEMENTS_INTERFACE,
                hierarchy.directRelationships("impl.Child").stream()
                        .filter(edge -> edge.target().binaryName().equals("api.ChildApi"))
                        .findFirst().orElseThrow().kind());
        assertEquals(HierarchyRelationshipKind.EXTENDS_INTERFACE,
                hierarchy.directRelationships("api.ChildApi").getFirst().kind());
        assertEquals(1, hierarchy.directRelationships("api.ChildApi").size());
        assertEquals(Assignability.YES, hierarchy.isAssignable("api.Parent", "impl.Child"));
        assertEquals(Assignability.YES, hierarchy.isAssignable("java.lang.Object", "api.ChildApi"));
        assertEquals(Assignability.NO, hierarchy.isAssignable("unrelated.Type", "impl.Child"));
    }

    @Test
    void missingHierarchyEvidenceProducesUnknownInsteadOfAFalseNo() throws IOException {
        write("impl/Child.class", classBytes("impl.Child", builder -> builder
                .withSuperclass(ClassDesc.of("external.Missing"))));
        TypeHierarchy hierarchy = TypeHierarchy.of(importTypes());

        HierarchyQueryResult result = hierarchy.transitiveSupertypes("impl.Child");

        assertFalse(result.complete());
        assertEquals(List.of(new JavaTypeName("external.Missing")), result.missingTypes());
        assertEquals(Assignability.YES, hierarchy.isAssignable("external.Missing", "impl.Child"));
        assertEquals(Assignability.UNKNOWN, hierarchy.isAssignable("other.Type", "impl.Child"));
    }

    @Test
    void completeExternalStubsCanProveNegativeAssignability() {
        TypeHierarchy hierarchy = TypeHierarchy.builder()
                .addExternal(ExternalTypeStub.complete(
                        "external.Leaf", JavaTypeKind.CLASS, null, List.of()))
                .build();

        assertEquals(Assignability.NO, hierarchy.isAssignable("other.Type", "external.Leaf"));
    }

    @Test
    void cyclesTerminateAndRemainExplicitlyUnknown() {
        TypeHierarchy hierarchy = TypeHierarchy.builder()
                .addExternal(ExternalTypeStub.complete(
                        "cycle.A", JavaTypeKind.CLASS, new JvmReferenceType("cycle.B"), List.of()))
                .addExternal(ExternalTypeStub.complete(
                        "cycle.B", JavaTypeKind.CLASS, new JvmReferenceType("cycle.A"), List.of()))
                .build();

        HierarchyQueryResult result = hierarchy.transitiveSupertypes("cycle.A");

        assertTrue(result.cycleDetected());
        assertFalse(result.complete());
        assertEquals(List.of(new JavaTypeName("cycle.B")), result.supertypes());
        assertEquals(Assignability.UNKNOWN, hierarchy.isAssignable("cycle.C", "cycle.A"));
    }

    @Test
    void unknownStartingTypesTerminateAsMissing() {
        HierarchyQueryResult result = TypeHierarchy.builder().build()
                .transitiveSupertypes("missing.Start");

        assertFalse(result.complete());
        assertEquals(List.of(new JavaTypeName("missing.Start")), result.missingTypes());
    }

    private byte[] classBytes(
            String binaryName, java.util.function.Consumer<java.lang.classfile.ClassBuilder> customizer) {
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
