package dev.archunitjava.selector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileOrigin;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeKind;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelDiagnostic;
import dev.archunitjava.model.TypeModelDiagnosticCode;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeAndPackageSelectorTest {
    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importModel() throws IOException {
        write("sample/Host.class", ClassFile.of().build(ClassDesc.of("sample.Host"), builder -> {}));
        InnerClassInfo member = InnerClassInfo.of(
                ClassDesc.of("sample.Host$Member"),
                Optional.of(ClassDesc.of("sample.Host")),
                Optional.of("Member"),
                ClassFile.ACC_PUBLIC);
        write("sample/Host$Member.class", ClassFile.of().build(
                ClassDesc.of("sample.Host$Member"),
                builder -> builder.with(InnerClassesAttribute.of(member))));
        write("other/Contract.class", ClassFile.of().build(
                ClassDesc.of("other.Contract"),
                builder -> builder.withFlags(ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT)));
        model = importTypes();
    }

    @Test
    void keepsBinaryCanonicalSimplePackageAndResourceDomainsDistinct() {
        assertEquals(List.of("sample.Host$Member"), names(TypeSelector.binaryName(
                exactName("sample.Host$Member")).selectFrom(model)));
        assertEquals(List.of("sample.Host$Member"), names(TypeSelector.canonicalName(
                exactName("sample.Host.Member")).selectFrom(model)));
        assertEquals(List.of("sample.Host$Member"), names(TypeSelector.simpleName(
                exactName("Member")).selectFrom(model)));
        assertEquals(List.of("sample.Host", "sample.Host$Member"), names(TypeSelector.packageName(
                exactName("sample")).selectFrom(model)));
        assertEquals(List.of("other.Contract"), names(TypeSelector.resourcePath(
                JavaPattern.glob(PatternDomain.RESOURCE_PATH, "other/*.class")).selectFrom(model)));

        assertThrows(IllegalArgumentException.class, () -> TypeSelector.resourcePath(
                exactName("sample.Host")));
        assertFalse(TypeSelector.canonicalName(exactName("sample.Host$Member"))
                .matches(model.types().getLast(), model.types()));
    }

    @Test
    void kindLocationAndPackageSelectorsAreStableAndReusable() {
        TypeSelector interfaces = TypeSelector.kind(JavaTypeKind.INTERFACE);
        PackageSelector samplePackages = PackageSelector.containing(
                TypeSelector.simpleName(exactName("Member")));

        assertEquals(List.of("other.Contract"), names(interfaces.selectFrom(model)));
        assertEquals(names(interfaces.selectFrom(model)), names(interfaces.selectFrom(model)));
        assertEquals(3, TypeSelector.inputKind(ClassFileInput.Kind.DIRECTORY)
                .selectFrom(model).selected().size());
        assertEquals(List.of("sample"), samplePackages.selectFrom(model).selected().stream()
                .map(value -> value.name().value()).toList());
        assertEquals(List.of("other", "sample"), PackageSelector.named().selectFrom(model)
                .selected().stream().map(value -> value.name().value()).toList());
        assertTrue(interfaces.description().text().contains("INTERFACE"));
    }

    @Test
    void incompleteModelsRetainImportAndCanonicalNameDiagnostics() throws IOException {
        Path isolated = temporaryDirectory.resolve("isolated");
        InnerClassInfo missingOwner = InnerClassInfo.of(
                ClassDesc.of("broken.Missing$Child"),
                Optional.of(ClassDesc.of("broken.Missing")),
                Optional.of("Child"),
                0);
        write(isolated, "broken/Missing$Child.class", ClassFile.of().build(
                ClassDesc.of("broken.Missing$Child"),
                builder -> builder.with(InnerClassesAttribute.of(missingOwner))));
        TypeModelResult imported = importTypes(isolated);
        TypeModelDiagnostic retained = new TypeModelDiagnostic(
                TypeModelDiagnosticCode.INVALID_BINARY_NAME,
                "broken.class",
                new ClassFileOrigin(ClassFileInput.Kind.DIRECTORY, "input", "broken.class"),
                Map.of("reason", "fixture"));
        TypeModelResult incomplete = new TypeModelResult(
                imported.types(), List.of(), List.of(), List.of(retained));

        TypeSelection result = TypeSelector.canonicalName(
                exactName("broken.Missing.Child")).selectFrom(incomplete);

        assertTrue(result.isEmpty());
        assertTrue(result.importWasIncomplete());
        assertEquals(List.of(retained), result.modelDiagnostics());
        assertEquals(SelectionDiagnosticCode.MISSING_LEXICAL_OWNER,
                result.selectionDiagnostics().getFirst().code());
    }

    private static List<String> names(TypeSelection selection) {
        return selection.selected().stream().map(JavaType::binaryName).sorted().toList();
    }

    private static JavaPattern exactName(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private TypeModelResult importTypes() throws IOException {
        return importTypes(temporaryDirectory);
    }

    private TypeModelResult importTypes(Path root) throws IOException {
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(root)))
                .resources();
        return new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        write(temporaryDirectory, resourceName, bytes);
    }

    private static void write(Path root, String resourceName, byte[] bytes) throws IOException {
        Path target = root.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
