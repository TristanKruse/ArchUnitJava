package dev.archunitjava.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberKind;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JvmPrimitiveType;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.JvmVoidType;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberSelectorTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc STRING = ClassDesc.of("java.lang.String");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importModel() throws IOException {
        write("api/Service.class", ClassFile.of().build(ClassDesc.of("api.Service"), builder -> builder
                .withField("count", INT, ClassFile.ACC_PRIVATE)
                .withMethodBody("<init>", MethodTypeDesc.of(VOID), ClassFile.ACC_PUBLIC,
                        code -> code.return_())
                .withMethodBody("<clinit>", MethodTypeDesc.of(VOID), ClassFile.ACC_STATIC,
                        code -> code.return_())
                .withMethod("call", MethodTypeDesc.of(VOID), ClassFile.ACC_ABSTRACT, ignored -> {})
                .withMethod("call", MethodTypeDesc.of(STRING, INT),
                        ClassFile.ACC_ABSTRACT, ignored -> {})));
        write("internal/Helper.class", ClassFile.of().build(
                ClassDesc.of("internal.Helper"),
                builder -> builder.withMethod("help", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_ABSTRACT, ignored -> {})));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void selectsOverloadsByExactDescriptorParametersAndReturnType() {
        MemberSelection byDescriptor = MemberSelector.descriptor("(I)Ljava/lang/String;")
                .selectFrom(model);
        MemberSelection byParameters = MemberSelector.parameterTypes(JvmPrimitiveType.INT)
                .selectFrom(model);
        MemberSelection byReturn = MemberSelector.returnType(
                new JvmReferenceType("java.lang.String")).selectFrom(model);

        assertEquals(List.of("api.Service#call(I)Ljava/lang/String;"), keys(byDescriptor));
        assertEquals(keys(byDescriptor), keys(byParameters));
        assertEquals(keys(byDescriptor), keys(byReturn));
        assertEquals(2, MemberSelector.named("call").selectFrom(model).selected().size());
        assertThrows(IllegalArgumentException.class, () -> MemberSelector.descriptor("not-a-descriptor"));
    }

    @Test
    void constructorsInitializersFieldsAndCodeUnitsHaveExplicitVocabulary() {
        assertEquals(List.of(JavaMemberKind.CONSTRUCTOR), kinds(
                MemberSelector.constructors().selectFrom(model)));
        assertEquals(List.of(JavaMemberKind.STATIC_INITIALIZER), kinds(
                MemberSelector.staticInitializers().selectFrom(model)));
        assertEquals(List.of("api.Service#countI"), keys(
                MemberSelector.fieldType(JvmPrimitiveType.INT).selectFrom(model)));
        assertTrue(MemberSelector.codeUnits().selectFrom(model).selected().stream()
                .noneMatch(member -> member.kind() == JavaMemberKind.FIELD));
        assertEquals(3, MemberSelector.returnType(JvmVoidType.VOID)
                .selectFrom(TypeSelector.binaryName(exactName("api.Service")).selectFrom(model))
                .selected().size());
    }

    @Test
    void declaringTypeAndPackageSelectorsComposeWithoutLosingStableOrder() {
        MemberSelector apiOwners = MemberSelector.declaredBy(
                TypeSelector.packageName(exactName("api")));
        MemberSelector internalPackage = MemberSelector.declaredIn(
                PackageSelector.name(exactName("internal")));

        assertEquals(5, apiOwners.selectFrom(model).selected().size());
        assertEquals(List.of("internal.Helper#help()V"), keys(internalPackage.selectFrom(model)));
        assertEquals(keys(apiOwners.selectFrom(model)), keys(apiOwners.selectFrom(model)));
        assertTrue(MemberSelector.name(JavaPattern.glob(
                        PatternDomain.QUALIFIED_NAME, "c*"))
                .description().text().contains("member name"));
    }

    private static List<String> keys(MemberSelection selection) {
        return selection.selected().stream()
                .map(member -> member.signature().stableKey())
                .toList();
    }

    private static List<JavaMemberKind> kinds(MemberSelection selection) {
        return selection.selected().stream().map(JavaMember::kind).toList();
    }

    private static JavaPattern exactName(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
