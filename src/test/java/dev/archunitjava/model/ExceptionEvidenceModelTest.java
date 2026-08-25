package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExceptionEvidenceModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc IO_EXCEPTION = ClassDesc.of("java.io.IOException");
    private static final ClassDesc SQL_EXCEPTION = ClassDesc.of("java.sql.SQLException");

    @TempDir Path temporaryDirectory;

    @Test
    void declaredThrowsAndCaughtHandlersRemainDistinct() throws IOException {
        JavaMember member = importMember(exceptionClass());

        List<JavaExceptionEvidence> ioEvidence = member.exceptionEvidence().stream()
                .filter(value -> value.targetType()
                        .map(JvmReferenceType::binaryName)
                        .orElse("")
                        .equals("java.io.IOException"))
                .toList();

        assertEquals(List.of(
                        JavaExceptionEvidenceKind.DECLARED_THROWS,
                        JavaExceptionEvidenceKind.CAUGHT_HANDLER),
                ioEvidence.stream().map(JavaExceptionEvidence::kind).toList());
        assertTrue(ioEvidence.getFirst().bytecodeLocation().isEmpty());
        assertTrue(ioEvidence.getLast().bytecodeLocation().isPresent());
        assertEquals("java.sql.SQLException", member.exceptionEvidence().stream()
                .filter(value -> value.kind() == JavaExceptionEvidenceKind.DECLARED_THROWS)
                .map(value -> value.targetType().orElseThrow().binaryName())
                .filter(value -> !value.equals("java.io.IOException"))
                .findFirst().orElseThrow());
    }

    @Test
    void catchAllHandlersNeverManufactureAThrowableTarget() throws IOException {
        JavaExceptionEvidence catchAll = importMember(exceptionClass()).exceptionEvidence().stream()
                .filter(value -> value.kind() == JavaExceptionEvidenceKind.CATCH_ALL_HANDLER)
                .findFirst().orElseThrow();

        assertTrue(catchAll.targetType().isEmpty());
        assertEquals(30, catchAll.bytecodeLocation().orElseThrow().lineNumber().orElseThrow());
    }

    @Test
    void throwInstructionsRetainOnlyTheInstructionFactAndLocation() throws IOException {
        JavaExceptionEvidence thrown = importMember(exceptionClass()).exceptionEvidence().stream()
                .filter(value -> value.kind() == JavaExceptionEvidenceKind.THROW_INSTRUCTION)
                .findFirst().orElseThrow();

        assertTrue(thrown.targetType().isEmpty());
        assertEquals(1, thrown.bytecodeLocation().orElseThrow().bytecodeOffset());
        assertEquals(10, thrown.bytecodeLocation().orElseThrow().lineNumber().orElseThrow());
    }

    @Test
    void exceptionCollectionsAreImmutableAndDeterministic() throws IOException {
        JavaMember member = importMember(exceptionClass());

        assertEquals(List.of(
                        JavaExceptionEvidenceKind.DECLARED_THROWS,
                        JavaExceptionEvidenceKind.DECLARED_THROWS,
                        JavaExceptionEvidenceKind.CAUGHT_HANDLER,
                        JavaExceptionEvidenceKind.CATCH_ALL_HANDLER,
                        JavaExceptionEvidenceKind.THROW_INSTRUCTION),
                member.exceptionEvidence().stream().map(JavaExceptionEvidence::kind).toList());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> member.exceptionEvidence().clear());
    }

    private byte[] exceptionClass() {
        return ClassFile.of().build(ClassDesc.of("sample.Exceptions"), builder -> builder.withMethod(
                "run",
                MethodTypeDesc.of(VOID),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                method -> method
                        .with(ExceptionsAttribute.ofSymbols(IO_EXCEPTION, SQL_EXCEPTION))
                        .withCode(code -> {
                            var start = code.newLabel();
                            var end = code.newLabel();
                            var typedHandler = code.newLabel();
                            var catchAllHandler = code.newLabel();
                            code.labelBinding(start)
                                    .lineNumber(10)
                                    .aconst_null()
                                    .athrow()
                                    .labelBinding(end)
                                    .labelBinding(typedHandler)
                                    .lineNumber(20)
                                    .pop()
                                    .return_()
                                    .labelBinding(catchAllHandler)
                                    .lineNumber(30)
                                    .pop()
                                    .return_()
                                    .exceptionCatch(start, end, typedHandler, IO_EXCEPTION)
                                    .exceptionCatchAll(start, end, catchAllHandler);
                        })));
    }

    private JavaMember importMember(byte[] bytes) throws IOException {
        Path file = temporaryDirectory.resolve("sample/Exceptions.class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource))
                .types().getFirst().declaredMembers().getFirst();
    }
}
