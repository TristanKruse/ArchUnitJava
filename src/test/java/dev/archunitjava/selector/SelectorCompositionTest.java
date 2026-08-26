package dev.archunitjava.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeKind;
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

class SelectorCompositionTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importModel() throws IOException {
        write("api/PublicApi.class", ClassFile.of().build(
                ClassDesc.of("api.PublicApi"), builder -> builder
                        .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE
                                | ClassFile.ACC_ABSTRACT)
                        .withMethod("call", MethodTypeDesc.of(VOID),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {})));
        write("api/Internal.class", ClassFile.of().build(
                ClassDesc.of("api.Internal"), builder -> builder
                        .withMethod("work", MethodTypeDesc.of(VOID),
                                ClassFile.ACC_PRIVATE | ClassFile.ACC_ABSTRACT, ignored -> {})));
        write("impl/Adapter.class", ClassFile.of().build(
                ClassDesc.of("impl.Adapter"), builder -> builder
                        .withFlags(ClassFile.ACC_PUBLIC)
                        .withMethod("adapt", MethodTypeDesc.of(VOID),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {})));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void typedBooleanGroupsHaveCanonicalReadableDescriptions() {
        TypeSelector inApi = TypeSelector.packageName(exact("api"));
        TypeSelector interfaces = TypeSelector.kind(JavaTypeKind.INTERFACE);
        TypeSelector first = TypeSelector.allOf(inApi, interfaces);
        TypeSelector reordered = TypeSelector.allOf(interfaces, inApi);

        assertEquals(first.description(), reordered.description());
        assertTrue(first.description().text().contains(" AND "));
        assertEquals(List.of("api.PublicApi"), typeNames(first.selectFrom(model)));
        assertEquals(List.of("api.Internal", "api.PublicApi"), typeNames(
                TypeSelector.anyOf(interfaces, TypeSelector.simpleName(exact("Internal")))
                        .selectFrom(model)));
        assertEquals(List.of("api.Internal", "impl.Adapter"), typeNames(
                interfaces.not().selectFrom(model)));
    }

    @Test
    void exclusionsUseTheSameImmutableSemanticsInEverySelectorDomain() {
        TypeSelector allTypes = TypeSelector.all();
        TypeSelector withoutImpl = allTypes.excluding(TypeSelector.packageName(exact("impl")));
        PackageSelector allPackages = PackageSelector.all();
        PackageSelector withoutImplPackage = allPackages.excluding(
                PackageSelector.name(exact("impl")));
        MemberSelector allMembers = MemberSelector.all();
        MemberSelector withoutPrivate = allMembers.excluding(
                MemberSelector.visibility(MemberVisibility.PRIVATE));

        assertEquals(3, allTypes.selectFrom(model).selected().size());
        assertEquals(List.of("api.Internal", "api.PublicApi"), typeNames(
                withoutImpl.selectFrom(model)));
        assertEquals(List.of("api"), withoutImplPackage.selectFrom(model).selected().stream()
                .map(value -> value.name().value()).toList());
        assertEquals(List.of("api.PublicApi#call()V", "impl.Adapter#adapt()V"),
                withoutPrivate.selectFrom(model).selected().stream()
                        .map(member -> member.signature().stableKey()).toList());
        assertEquals(3, allMembers.selectFrom(model).selected().size());
    }

    @Test
    void universalAndEmptySelectorsAreExplicitAndEmptyGroupsAreRejected() {
        assertEquals(SelectorConstant.UNIVERSAL, TypeSelector.all().constant());
        assertEquals(SelectorConstant.EMPTY, TypeSelector.none().constant());
        assertEquals(SelectorConstant.EMPTY, TypeSelector.all().not().constant());
        assertEquals(SelectorConstant.UNIVERSAL, TypeSelector.none().not().constant());
        assertEquals(SelectorConstant.EMPTY,
                TypeSelector.all().excluding(TypeSelector.all()).constant());
        TypeSelector failingUnknown = TypeSelector.assignableTo(
                "missing.Target", UnknownHierarchyPolicy.FAIL);
        assertEquals(3, TypeSelector.anyOf(failingUnknown, TypeSelector.all())
                .selectFrom(model).selected().size());
        assertTrue(TypeSelector.allOf(failingUnknown, TypeSelector.none())
                .selectFrom(model).isEmpty());
        assertTrue(TypeSelector.none().selectFrom(model).isEmpty());
        assertNotEquals(TypeSelector.all().description(), TypeSelector.none().description());
        assertThrows(IllegalArgumentException.class, () -> TypeSelector.allOf(List.of()));
        assertThrows(IllegalArgumentException.class, () -> PackageSelector.anyOf(List.of()));
        assertThrows(IllegalArgumentException.class, () -> MemberSelector.allOf(List.of()));
    }

    private static JavaPattern exact(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private static List<String> typeNames(TypeSelection selection) {
        return selection.selected().stream().map(JavaType::binaryName).sorted().toList();
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
