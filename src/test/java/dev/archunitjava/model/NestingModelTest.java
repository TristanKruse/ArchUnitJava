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
import dev.archunitjava.importer.ParsedEnclosingMethod;
import dev.archunitjava.importer.ParsedInnerClass;
import dev.archunitjava.importer.ParsedNestingMetadata;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.EnclosingMethodAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.attribute.NestMembersAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NestingModelTest {
    private static final ClassDesc HOST = ClassDesc.of("sample.Host");
    private static final MethodTypeDesc RUN = MethodTypeDesc.ofDescriptor("()V");

    @TempDir Path temporaryDirectory;

    @Test
    void topLevelMemberLocalAndAnonymousTypesRemainDistinguishable() throws IOException {
        write("sample/Host.class", ClassFile.of().build(HOST, builder -> builder
                .with(NestMembersAttribute.ofSymbols(
                        ClassDesc.of("sample.Host$Member"),
                        ClassDesc.of("sample.Host$1Local"),
                        ClassDesc.of("sample.Host$1")))));
        write("sample/Host$Member.class", nestedClass(
                "sample.Host$Member",
                InnerClassInfo.of(
                        ClassDesc.of("sample.Host$Member"),
                        Optional.of(HOST),
                        Optional.of("Member"),
                        ClassFile.ACC_PUBLIC),
                null));
        write("sample/Host$1Local.class", nestedClass(
                "sample.Host$1Local",
                InnerClassInfo.of(
                        ClassDesc.of("sample.Host$1Local"),
                        Optional.empty(),
                        Optional.of("Local"),
                        0),
                EnclosingMethodAttribute.of(HOST, Optional.of("run"), Optional.of(RUN))));
        write("sample/Host$1.class", nestedClass(
                "sample.Host$1",
                InnerClassInfo.of(
                        ClassDesc.of("sample.Host$1"),
                        Optional.empty(),
                        Optional.empty(),
                        0),
                EnclosingMethodAttribute.of(HOST, Optional.of("run"), Optional.of(RUN))));

        Map<String, JavaType> types = importTypes().stream()
                .collect(Collectors.toMap(JavaType::binaryName, Function.identity()));

        assertEquals(JavaNestingKind.TOP_LEVEL, types.get("sample.Host").nesting().kind());
        assertEquals(JavaNestingKind.MEMBER, types.get("sample.Host$Member").nesting().kind());
        assertEquals(JavaNestingKind.LOCAL, types.get("sample.Host$1Local").nesting().kind());
        assertEquals(JavaNestingKind.ANONYMOUS, types.get("sample.Host$1").nesting().kind());
        assertEquals("Local", types.get("sample.Host$1Local").nesting()
                .simpleName().orElseThrow());
        assertTrue(types.get("sample.Host$1").nesting().simpleName().isEmpty());
        assertEquals("run", types.get("sample.Host$1").nesting()
                .enclosingDeclaration().orElseThrow().methodName().orElseThrow());
    }

    @Test
    void nestmatesAreModeledSeparatelyFromLexicalOwnership() throws IOException {
        InnerClassInfo member = InnerClassInfo.of(
                ClassDesc.of("sample.Other$Member"),
                Optional.of(ClassDesc.of("sample.Other")),
                Optional.of("Member"),
                0);
        write("sample/Other$Member.class", ClassFile.of().build(
                ClassDesc.of("sample.Other$Member"), builder -> builder
                        .with(InnerClassesAttribute.of(member))
                        .with(NestHostAttribute.of(HOST))));

        JavaNesting nesting = importTypes().getFirst().nesting();

        assertEquals("sample.Other", nesting.lexicalOwner().orElseThrow().binaryName());
        assertEquals("sample.Host", nesting.nestHost().binaryName());
        assertFalse(nesting.lexicalOwner().orElseThrow().equals(nesting.nestHost()));
    }

    @Test
    void conflictingAttributesUseUnknownFallbackWithDeterministicDiagnostics() {
        ParsedNestingMetadata metadata = new ParsedNestingMetadata(
                List.of(
                        new ParsedInnerClass(
                                "sample.Conflict", Optional.of("sample.One"), Optional.of("A"), 0),
                        new ParsedInnerClass(
                                "sample.Conflict", Optional.of("sample.Two"), Optional.of("B"), 0)),
                List.of(
                        new ParsedEnclosingMethod(
                                "sample.Three", Optional.of("a"), Optional.of("()V")),
                        new ParsedEnclosingMethod(
                                "sample.Four", Optional.of("b"), Optional.of("()V"))),
                List.of("sample.HostA", "sample.HostB"),
                List.of("sample.Member", "sample.Member"));

        JavaNesting nesting = build(parsed("sample.Conflict", metadata)).nesting();

        assertEquals(JavaNestingKind.UNKNOWN, nesting.kind());
        assertTrue(nesting.lexicalOwner().isEmpty());
        assertEquals("sample.HostA", nesting.nestHost().binaryName());
        assertEquals(List.of("sample.Member"), nesting.declaredNestMembers().stream()
                .map(JavaTypeName::binaryName).toList());
        assertEquals(
                List.of(
                        NestingDiagnosticCode.CONFLICTING_ENCLOSING_METHOD_EVIDENCE,
                        NestingDiagnosticCode.CONFLICTING_INNER_CLASS_EVIDENCE,
                        NestingDiagnosticCode.CONFLICTING_NEST_HOST_EVIDENCE,
                        NestingDiagnosticCode.DUPLICATE_NEST_MEMBER,
                        NestingDiagnosticCode.NEST_HOST_AND_MEMBERS_DECLARED),
                nesting.diagnostics().stream().map(NestingDiagnostic::code).toList());
    }

    @Test
    void diagnosticsAreBoundedForHostileNestMemberTables() {
        List<String> members = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            members.add("sample.Member" + index);
            members.add("sample.Member" + index);
        }
        JavaNesting nesting = build(parsed(
                "sample.Hostile",
                new ParsedNestingMetadata(List.of(), List.of(), List.of(), members)))
                .nesting();

        assertEquals(JavaNesting.MAXIMUM_DIAGNOSTICS, nesting.diagnostics().size());
        assertTrue(nesting.diagnosticsTruncated());
    }

    private byte[] nestedClass(
            String binaryName,
            InnerClassInfo innerClass,
            EnclosingMethodAttribute enclosing) {
        return ClassFile.of().build(ClassDesc.of(binaryName), builder -> {
            builder.with(InnerClassesAttribute.of(innerClass));
            builder.with(NestHostAttribute.of(HOST));
            if (enclosing != null) builder.with(enclosing);
        });
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

    private static ParsedClassFile parsed(String binaryName, ParsedNestingMetadata metadata) {
        String resource = binaryName.replace('.', '/') + ".class";
        return new ParsedClassFile(
                binaryName,
                ClassFile.ACC_PUBLIC,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                resource,
                new ClassFileOrigin(ClassFileInput.Kind.DIRECTORY, "test-classes", resource),
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
                false,
                List.of(),
                metadata);
    }

    private static JavaType build(ParsedClassFile parsed) {
        return new TypeModelBuilder().build(new ClassFileReadResult(List.of(parsed), List.of()))
                .types().getFirst();
    }
}
