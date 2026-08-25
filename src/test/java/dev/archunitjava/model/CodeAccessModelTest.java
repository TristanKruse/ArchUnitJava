package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeAccessModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc TARGET = ClassDesc.of("target.Service");
    private static final ClassDesc API = ClassDesc.of("target.Api");

    @TempDir Path temporaryDirectory;

    @Test
    void extractsConstructorCallsMethodCallsAndFieldReadsAndWrites() throws IOException {
        JavaMember caller = importMethod(accessClass(true));
        Map<JavaCodeAccessOpcode, JavaCodeAccess> byOpcode = caller.codeAccesses().stream()
                .collect(Collectors.toMap(
                        JavaCodeAccess::opcode, Function.identity(), (first, ignored) -> first));

        assertEquals(JavaCodeAccessKind.FIELD_READ, byOpcode.get(JavaCodeAccessOpcode.GETSTATIC).kind());
        assertEquals(JavaCodeAccessKind.FIELD_READ, byOpcode.get(JavaCodeAccessOpcode.GETFIELD).kind());
        assertEquals(JavaCodeAccessKind.FIELD_WRITE, byOpcode.get(JavaCodeAccessOpcode.PUTSTATIC).kind());
        assertEquals(JavaCodeAccessKind.FIELD_WRITE, byOpcode.get(JavaCodeAccessOpcode.PUTFIELD).kind());
        assertEquals(JavaCodeAccessKind.CONSTRUCTOR_CALL,
                byOpcode.get(JavaCodeAccessOpcode.INVOKESPECIAL).kind());
        assertEquals("<init>", byOpcode.get(JavaCodeAccessOpcode.INVOKESPECIAL).target().name());
        assertEquals(JavaCodeAccessKind.METHOD_CALL,
                byOpcode.get(JavaCodeAccessOpcode.INVOKESTATIC).kind());
        assertEquals("target.Service", assertInstanceOf(
                JvmReferenceType.class,
                byOpcode.get(JavaCodeAccessOpcode.INVOKESTATIC).target().ownerType()).binaryName());
    }

    @Test
    void preservesDispatchOpcodeAndInterfaceEvidenceWithoutResolvingTargets() throws IOException {
        JavaMember caller = importMethod(accessClass(true));
        JavaCodeAccess virtual = access(caller, JavaCodeAccessOpcode.INVOKEVIRTUAL, "work");
        JavaCodeAccess interfaceCall = access(caller, JavaCodeAccessOpcode.INVOKEINTERFACE, "run");
        JavaCodeAccess special = access(caller, JavaCodeAccessOpcode.INVOKESPECIAL, "<init>");

        assertTrue(!virtual.interfaceTarget());
        assertTrue(interfaceCall.interfaceTarget());
        assertTrue(!special.interfaceTarget());
        assertEquals("target.Api", assertInstanceOf(
                JvmReferenceType.class, interfaceCall.target().ownerType()).binaryName());
        assertEquals("exercise", interfaceCall.caller().name());
        assertTrue(caller.codeAccesses().stream()
                .map(access -> access.location().bytecodeOffset())
                .reduce(-1, (previous, current) -> {
                    assertTrue(current > previous);
                    return current;
                }) >= 0);
    }

    @Test
    void sourceLinesAreOptionalAndArrayOwnersRemainRepresentable() throws IOException {
        JavaMember withLines = importMethod(accessClass(true));
        JavaMember withoutLines = importMethod(accessClass(false));

        assertTrue(withLines.codeAccesses().stream()
                .allMatch(access -> access.location().lineNumber().isPresent()));
        assertTrue(withoutLines.codeAccesses().stream()
                .allMatch(access -> access.location().lineNumber().isEmpty()));
        JavaCodeAccess clone = access(withLines, JavaCodeAccessOpcode.INVOKEVIRTUAL, "clone");
        assertInstanceOf(JvmArrayType.class, clone.target().ownerType());
    }

    @Test
    void malformedCodeResourceDoesNotDiscardValidBatchEvidence() throws IOException {
        byte[] valid = accessClass(true);
        byte[] truncated = Arrays.copyOf(valid, valid.length - 12);
        write("good/Accesses.class", valid);
        write("broken/Accesses.class", truncated);
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        var read = new ClassFileReader().readAll(resources);
        TypeModelResult model = new TypeModelBuilder().build(read);

        assertEquals(1, model.types().size());
        assertTrue(!model.types().getFirst().declaredMembers().getFirst().codeAccesses().isEmpty());
        assertEquals(1, model.classFileDiagnostics().size());
    }

    private byte[] accessClass(boolean debugLines) {
        return ClassFile.of().build(ClassDesc.of("good.Accesses"), builder -> {
            if (debugLines) builder.with(SourceFileAttribute.of("Accesses.java"));
            builder.withMethodBody(
                    "exercise",
                    MethodTypeDesc.of(VOID),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                    code -> {
                        if (debugLines) code.lineNumber(40);
                        code.getstatic(TARGET, "count", INT).pop()
                                .iconst_1().putstatic(TARGET, "count", INT)
                                .aconst_null().getfield(TARGET, "value", INT).pop()
                                .aconst_null().iconst_1().putfield(TARGET, "value", INT)
                                .new_(TARGET).dup()
                                .invokespecial(TARGET, "<init>", MethodTypeDesc.of(VOID)).pop()
                                .invokestatic(TARGET, "staticWork", MethodTypeDesc.of(VOID))
                                .aconst_null().invokevirtual(TARGET, "work", MethodTypeDesc.of(VOID))
                                .aconst_null().invokeinterface(API, "run", MethodTypeDesc.of(VOID))
                                .aconst_null().invokevirtual(
                                        ClassDesc.ofDescriptor("[Ljava/lang/String;"),
                                        "clone",
                                        MethodTypeDesc.of(OBJECT))
                                .pop()
                                .return_();
                    });
        });
    }

    private JavaMember importMethod(byte[] bytes) throws IOException {
        Path isolated = Files.createTempDirectory(temporaryDirectory, "case-");
        Path file = isolated.resolve("good/Accesses.class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(isolated)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource))
                .types().getFirst().declaredMembers().getFirst();
    }

    private void write(String resource, byte[] bytes) throws IOException {
        Path file = temporaryDirectory.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static JavaCodeAccess access(
            JavaMember member, JavaCodeAccessOpcode opcode, String targetName) {
        return member.codeAccesses().stream()
                .filter(access -> access.opcode() == opcode && access.target().name().equals(targetName))
                .findFirst().orElseThrow();
    }
}
