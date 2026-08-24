package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaMemberModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");

    @TempDir Path temporaryDirectory;

    @Test
    void importsFieldsMethodsConstructorsAndStaticInitializers() throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.Components"), builder -> builder
                .withField("value", INT, ClassFile.ACC_PRIVATE)
                .withMethodBody("<init>", MethodTypeDesc.of(VOID), ClassFile.ACC_PUBLIC,
                        code -> code.return_())
                .withMethodBody("run", MethodTypeDesc.of(VOID), ClassFile.ACC_PUBLIC,
                        code -> code.return_())
                .withMethodBody("<clinit>", MethodTypeDesc.of(VOID), ClassFile.ACC_STATIC,
                        code -> code.return_()));

        JavaType type = importType("sample/Components.class", bytes);

        assertEquals(
                List.of(JavaMemberKind.STATIC_INITIALIZER, JavaMemberKind.CONSTRUCTOR,
                        JavaMemberKind.METHOD, JavaMemberKind.FIELD),
                type.declaredMembers().stream().map(JavaMember::kind).toList());
        assertTrue(type.declaredMembers().stream()
                .filter(member -> member.kind() != JavaMemberKind.FIELD)
                .allMatch(JavaMember::hasCode));
        assertFalse(type.declaredMembers().stream()
                .filter(member -> member.kind() == JavaMemberKind.FIELD)
                .findFirst().orElseThrow().hasCode());
        assertEquals(JvmPrimitiveType.INT, type.declaredMembers().stream()
                .filter(member -> member.kind() == JavaMemberKind.FIELD)
                .findFirst().orElseThrow().fieldType());
        assertEquals(JvmVoidType.VOID, type.declaredMembers().stream()
                .filter(member -> member.kind() == JavaMemberKind.METHOD)
                .findFirst().orElseThrow().methodType().returnType());
    }

    @Test
    void overloadDescriptorsProduceUnambiguousStableSignatures() throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.Overloaded"), builder -> builder
                .withMethod("call", MethodTypeDesc.of(VOID), ClassFile.ACC_ABSTRACT, ignored -> {})
                .withMethod("call", MethodTypeDesc.of(VOID, INT), ClassFile.ACC_ABSTRACT, ignored -> {}));

        List<JavaMember> methods = importType("sample/Overloaded.class", bytes).declaredMembers();

        assertEquals(2, methods.size());
        assertNotEquals(methods.get(0).signature(), methods.get(1).signature());
        assertEquals(List.of("()V", "(I)V"), methods.stream().map(JavaMember::descriptor).toList());
        assertTrue(methods.stream().map(member -> member.signature().stableKey()).distinct().count() == 2);
    }

    @Test
    void compilerCreatedFlagsRemainRepresentable() throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.Generated"), builder -> builder
                .withField("$cache", INT, ClassFile.ACC_STATIC | ClassFile.ACC_SYNTHETIC)
                .withMethod("bridge", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_BRIDGE | ClassFile.ACC_SYNTHETIC,
                        ignored -> {}));

        JavaType type = importType("sample/Generated.class", bytes);

        assertTrue(type.declaredMembers().getFirst().modifiers().contains(JavaMemberModifier.SYNTHETIC));
        assertTrue(type.declaredMembers().getLast().modifiers().contains(JavaMemberModifier.BRIDGE));
    }

    @Test
    void memberOrderingDoesNotDependOnClassFileEncounterOrder() throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("sample.Unsorted"), builder -> builder
                .withField("z", INT, 0)
                .withMethod("z", MethodTypeDesc.of(VOID), ClassFile.ACC_ABSTRACT, ignored -> {})
                .withField("a", INT, 0)
                .withMethod("a", MethodTypeDesc.of(VOID), ClassFile.ACC_ABSTRACT, ignored -> {}));

        JavaType type = importType("sample/Unsorted.class", bytes);

        assertEquals(List.of("a", "a", "z", "z"),
                type.declaredMembers().stream().map(JavaMember::name).toList());
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
