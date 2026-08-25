package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileOrigin;
import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.ParsedBootstrapArgument;
import dev.archunitjava.importer.ParsedClassFile;
import dev.archunitjava.importer.ParsedDynamicCallSite;
import dev.archunitjava.importer.ParsedMember;
import dev.archunitjava.importer.ParsedMethodHandle;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicCallSiteModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc STRING = ClassDesc.of("java.lang.String");
    private static final ClassDesc LOOKUP = ClassDesc.of("java.lang.invoke.MethodHandles$Lookup");
    private static final ClassDesc METHOD_TYPE = ClassDesc.of("java.lang.invoke.MethodType");
    private static final ClassDesc METHOD_HANDLE = ClassDesc.of("java.lang.invoke.MethodHandle");
    private static final ClassDesc CALL_SITE = ClassDesc.of("java.lang.invoke.CallSite");
    private static final ClassDesc FUNCTION = ClassDesc.of("java.util.function.Function");

    @TempDir Path temporaryDirectory;

    @Test
    void lambdaMetafactoryRetainsImplementationHandleAndFunctionalInterface() throws IOException {
        JavaDynamicCallSite lambda = importMember(dynamicClass()).dynamicCallSites().getFirst();

        assertEquals(JavaDynamicCallSiteKind.LAMBDA_METAFACTORY, lambda.kind());
        assertEquals("java.util.function.Function",
                lambda.functionalInterfaces().getFirst().binaryName());
        JavaMethodHandle implementation = lambda.lambdaImplementation().orElseThrow();
        assertEquals(JavaMethodHandleKind.STATIC, implementation.kind());
        assertEquals("target.Functions", ((JvmReferenceType) implementation.ownerType()).binaryName());
        assertEquals("apply", implementation.name());
        assertEquals("(Ljava/lang/String;)Ljava/lang/String;", implementation.lookupDescriptor());
        assertEquals("apply", lambda.invocationName());
    }

    @Test
    void unknownBootstrapsRemainGenericWithRawEvidence() throws IOException {
        JavaDynamicCallSite generic = importMember(dynamicClass()).dynamicCallSites().stream()
                .filter(site -> site.invocationName().equals("custom"))
                .findFirst().orElseThrow();

        assertEquals(JavaDynamicCallSiteKind.GENERIC, generic.kind());
        assertEquals("bootstrap.Host", ((JvmReferenceType) generic.bootstrapMethod().ownerType())
                .binaryName());
        assertEquals("bootstrap", generic.bootstrapMethod().name());
        assertTrue(generic.lambdaImplementation().isEmpty());
        assertTrue(generic.functionalInterfaces().isEmpty());
    }

    @Test
    void stringConcatenationIsNeverMislabeledAsALambda() throws IOException {
        JavaDynamicCallSite concat = importMember(dynamicClass()).dynamicCallSites().stream()
                .filter(site -> site.invocationName().equals("makeConcatWithConstants"))
                .findFirst().orElseThrow();

        assertEquals(JavaDynamicCallSiteKind.STRING_CONCAT, concat.kind());
        assertTrue(concat.lambdaImplementation().isEmpty());
        assertTrue(concat.functionalInterfaces().isEmpty());
        assertEquals("STRING", concat.bootstrapArguments().getFirst().kind());
        assertEquals("\u0001", concat.bootstrapArguments().getFirst().encodedValue());
    }

    @Test
    void invokedynamicStaysSeparateFromResolvedLookingMemberCalls() throws IOException {
        JavaMember member = importMember(dynamicClass());

        assertEquals(3, member.dynamicCallSites().size());
        assertTrue(member.codeAccesses().isEmpty());
        assertEquals(List.of(
                        JavaDynamicCallSiteKind.LAMBDA_METAFACTORY,
                        JavaDynamicCallSiteKind.STRING_CONCAT,
                        JavaDynamicCallSiteKind.GENERIC),
                member.dynamicCallSites().stream().map(JavaDynamicCallSite::kind).toList());
        assertTrue(member.dynamicCallSites().stream()
                .allMatch(site -> site.location().lineNumber().orElseThrow() == 50));
    }

    @Test
    void bootstrapInterpretationAndArgumentRetentionAreBounded() {
        ParsedMethodHandle lambdaBootstrap = new ParsedMethodHandle(
                "STATIC",
                6,
                "Ljava/lang/invoke/LambdaMetafactory;",
                "metafactory",
                "()Ljava/lang/invoke/CallSite;",
                false);
        List<ParsedBootstrapArgument> arguments = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            arguments.add(new ParsedBootstrapArgument(
                    "INTEGER", Integer.toString(index), Optional.empty()));
        }
        ParsedDynamicCallSite malformedLambda = new ParsedDynamicCallSite(
                "apply", "()Ljava/util/function/Function;", lambdaBootstrap, arguments, 0);
        ParsedMember member = new ParsedMember(
                ParsedMember.Kind.METHOD,
                "run",
                "()V",
                ClassFile.ACC_PUBLIC,
                true,
                List.of(),
                Optional.empty(),
                List.of(),
                List.of(malformedLambda));
        JavaDynamicCallSite site = build(member).dynamicCallSites().getFirst();

        assertEquals(JavaDynamicCallSiteKind.GENERIC, site.kind());
        assertEquals(300, site.originalBootstrapArgumentCount());
        assertEquals(JavaDynamicCallSite.MAXIMUM_BOOTSTRAP_ARGUMENTS,
                site.bootstrapArguments().size());
        assertTrue(site.bootstrapArgumentsTruncated());
    }

    private byte[] dynamicClass() {
        DynamicCallSiteDesc lambda = DynamicCallSiteDesc.of(
                lambdaBootstrap(),
                "apply",
                MethodTypeDesc.of(FUNCTION),
                MethodTypeDesc.of(OBJECT, OBJECT),
                MethodHandleDesc.ofMethod(
                        DirectMethodHandleDesc.Kind.STATIC,
                        ClassDesc.of("target.Functions"),
                        "apply",
                        MethodTypeDesc.of(STRING, STRING)),
                MethodTypeDesc.of(STRING, STRING));
        DynamicCallSiteDesc concat = DynamicCallSiteDesc.of(
                concatBootstrap(),
                "makeConcatWithConstants",
                MethodTypeDesc.of(STRING, STRING),
                "\u0001");
        DynamicCallSiteDesc unknown = DynamicCallSiteDesc.of(
                unknownBootstrap(), "custom", MethodTypeDesc.of(OBJECT));
        return ClassFile.of().build(ClassDesc.of("sample.Dynamic"), builder -> builder
                .withMethodBody(
                        "run",
                        MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        code -> code.lineNumber(50)
                                .invokedynamic(lambda).pop()
                                .aconst_null().invokedynamic(concat).pop()
                                .invokedynamic(unknown).pop()
                                .return_()));
    }

    private static DirectMethodHandleDesc lambdaBootstrap() {
        return MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC,
                ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
                "metafactory",
                MethodTypeDesc.of(
                        CALL_SITE,
                        LOOKUP,
                        STRING,
                        METHOD_TYPE,
                        METHOD_TYPE,
                        METHOD_HANDLE,
                        METHOD_TYPE));
    }

    private static DirectMethodHandleDesc concatBootstrap() {
        return MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC,
                ClassDesc.of("java.lang.invoke.StringConcatFactory"),
                "makeConcatWithConstants",
                MethodTypeDesc.of(
                        CALL_SITE,
                        LOOKUP,
                        STRING,
                        METHOD_TYPE,
                        STRING,
                        ClassDesc.ofDescriptor("[Ljava/lang/Object;")));
    }

    private static DirectMethodHandleDesc unknownBootstrap() {
        return MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC,
                ClassDesc.of("bootstrap.Host"),
                "bootstrap",
                MethodTypeDesc.of(CALL_SITE, LOOKUP, STRING, METHOD_TYPE));
    }

    private JavaMember importMember(byte[] bytes) throws IOException {
        Path file = temporaryDirectory.resolve("sample/Dynamic.class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource))
                .types().getFirst().declaredMembers().getFirst();
    }

    private static JavaMember build(ParsedMember member) {
        ParsedClassFile parsed = new ParsedClassFile(
                "sample.Bounded",
                ClassFile.ACC_PUBLIC,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                "sample/Bounded.class",
                new ClassFileOrigin(
                        ClassFileInput.Kind.DIRECTORY, "test-classes", "sample/Bounded.class"),
                0,
                Optional.empty(),
                List.of(member));
        return new TypeModelBuilder().build(new ClassFileReadResult(List.of(parsed), List.of()))
                .types().getFirst().declaredMembers().getFirst();
    }
}
