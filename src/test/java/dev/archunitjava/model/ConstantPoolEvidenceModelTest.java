package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConstantPoolEvidenceModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc STRING = ClassDesc.of("java.lang.String");
    private static final ClassDesc INTEGER = ClassDesc.of("java.lang.Integer");
    private static final ClassDesc TARGET = ClassDesc.of("target.Actions");

    @TempDir Path temporaryDirectory;

    @Test
    void classLiteralsHaveTypedTargetsAndExactLoadSites() throws IOException {
        JavaConstantEvidence literal = importType(constantClass()).constantPoolEvidence().constants().stream()
                .filter(value -> value.kind() == JavaConstantEvidenceKind.CLASS_LITERAL)
                .filter(value -> value.descriptor().equals("Ltarget/Literal;"))
                .findFirst().orElseThrow();

        assertEquals(List.of("target.Literal"), literal.referencedTypes().stream()
                .map(type -> ((JvmReferenceType) type).binaryName())
                .toList());
        assertEquals("run", literal.loadSite().orElseThrow().owner().name());
        assertEquals(40, literal.loadSite().orElseThrow().location()
                .lineNumber().orElseThrow());
    }

    @Test
    void methodTypesAndHandlesPreserveTypedSignaturesAndReferenceKinds() throws IOException {
        JavaConstantPoolEvidence pool = importType(constantClass()).constantPoolEvidence();
        JavaConstantEvidence methodType = pool.constants().stream()
                .filter(value -> value.kind() == JavaConstantEvidenceKind.METHOD_TYPE)
                .filter(value -> value.descriptor()
                        .equals("(Ljava/lang/String;)Ljava/lang/Integer;"))
                .findFirst().orElseThrow();
        assertEquals(List.of("java.lang.Integer", "java.lang.String"), methodType.referencedTypes().stream()
                .map(type -> ((JvmReferenceType) type).binaryName())
                .sorted()
                .toList());

        JavaMethodHandle handle = pool.constants().stream()
                .filter(value -> value.kind() == JavaConstantEvidenceKind.METHOD_HANDLE)
                .map(value -> value.methodHandle().orElseThrow())
                .filter(value -> value.name().equals("apply"))
                .findFirst().orElseThrow();
        assertEquals(JavaMethodHandleKind.STATIC, handle.kind());
        assertEquals(6, handle.referenceKind());
        assertEquals("target.Actions", ((JvmReferenceType) handle.ownerType()).binaryName());
        assertEquals("(Ljava/lang/String;)Ljava/lang/String;", handle.lookupDescriptor());
    }

    @Test
    void dynamicConstantsRetainBootstrapProvenanceWithoutResolution() throws IOException {
        JavaDynamicConstant dynamic = importType(constantClass()).constantPoolEvidence().constants().stream()
                .filter(value -> value.kind() == JavaConstantEvidenceKind.DYNAMIC_CONSTANT)
                .map(value -> value.dynamicConstant().orElseThrow())
                .filter(value -> value.name().equals("VALUE"))
                .findFirst().orElseThrow();

        assertEquals("java.lang.String", ((JvmReferenceType) dynamic.constantType()).binaryName());
        assertEquals("java.lang.invoke.ConstantBootstraps",
                ((JvmReferenceType) dynamic.bootstrapMethod().ownerType()).binaryName());
        assertEquals("nullConstant", dynamic.bootstrapMethod().name());
        assertTrue(dynamic.bootstrapArguments().isEmpty());
    }

    @Test
    void retainedConstantEvidenceHasAHardDeterministicLimit() {
        List<JavaConstantEvidence> evidence = new ArrayList<>();
        for (int index = 1; index <= 4200; index++) {
            evidence.add(new JavaConstantEvidence(
                    JavaConstantEvidenceKind.METHOD_TYPE,
                    index,
                    "()V",
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()));
        }
        JavaConstantPoolEvidence pool = new JavaConstantPoolEvidence(evidence, 4200, true);

        assertEquals(JavaConstantPoolEvidence.MAXIMUM_CONSTANTS, pool.constants().size());
        assertEquals(1, pool.constants().getFirst().constantPoolIndex());
        assertEquals(JavaConstantPoolEvidence.MAXIMUM_CONSTANTS,
                pool.constants().getLast().constantPoolIndex());
        assertTrue(pool.truncated());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> pool.constants().clear());
    }

    private byte[] constantClass() {
        MethodTypeDesc methodType = MethodTypeDesc.of(INTEGER, STRING);
        DirectMethodHandleDesc handle = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC,
                TARGET,
                "apply",
                MethodTypeDesc.of(STRING, STRING));
        DynamicConstantDesc<?> dynamic = DynamicConstantDesc.ofNamed(
                ConstantDescs.BSM_NULL_CONSTANT, "VALUE", STRING);
        return ClassFile.of().build(ClassDesc.of("sample.Constants"), builder -> builder
                .withMethodBody(
                        "run",
                        MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        code -> code.lineNumber(40)
                                .ldc(ClassDesc.of("target.Literal")).pop()
                                .ldc(methodType).pop()
                                .ldc(handle).pop()
                                .ldc(dynamic).pop()
                                .return_()));
    }

    private JavaType importType(byte[] bytes) throws IOException {
        Path file = temporaryDirectory.resolve("sample/Constants.class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource))
                .types().getFirst();
    }
}
