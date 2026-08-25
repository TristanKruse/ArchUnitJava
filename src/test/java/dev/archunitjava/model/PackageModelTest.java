package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackageModelTest {
    @TempDir Path temporaryDirectory;

    @Test
    void namedAndUnnamedPackagesAreDistinctIdentifiers() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        write(first, "Loose.class", classBytes("Loose"));
        write(first, "sample/Named.class", classBytes("sample.Named"));

        JavaPackageIndex packages = importPackages(List.of(first));

        assertEquals(List.of("", "sample"), packages.all().stream()
                .map(value -> value.name().value()).toList());
        assertTrue(packages.find(JavaPackageName.unnamed()).orElseThrow().name().isUnnamed());
        assertEquals("<unnamed>", JavaPackageName.unnamed().displayName());
        assertFalse(JavaPackageName.named("sample").isUnnamed());
    }

    @Test
    void packageInfoMetadataIsExposedWithoutOrdinaryTypeNoise() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        write(first, "sample/package-info.class", ClassFile.of().build(
                ClassDesc.of("sample.package-info"), builder -> builder
                        .with(RuntimeInvisibleAnnotationsAttribute.of(
                                Annotation.of(ClassDesc.of("ann.DocumentedPackage"))))));
        write(first, "sample/Service.class", classBytes("sample.Service"));

        JavaPackage javaPackage = importPackages(List.of(first)).find("sample").orElseThrow();

        assertEquals(List.of("sample.Service"), javaPackage.types().stream()
                .map(JavaType::binaryName).toList());
        assertEquals(List.of("sample.package-info"), javaPackage.packageInfoTypes().stream()
                .map(JavaType::binaryName).toList());
        assertEquals("sample.package-info", javaPackage.packageInfoType().orElseThrow().binaryName());
        assertEquals(List.of("ann.DocumentedPackage"), javaPackage.annotations().stream()
                .map(value -> value.annotation().type().binaryName()).toList());
        assertEquals(AnnotationSiteKind.PACKAGE_DECLARATION,
                javaPackage.annotations().getFirst().site().kind());
        assertEquals("package:sample", javaPackage.annotations().getFirst().site().ownerKey());
    }

    @Test
    void splitPackagesRetainEveryInputOriginInPrecedenceOrder() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");
        write(first, "shared/First.class", classBytes("shared.First"));
        write(first, "shared/AlsoFirst.class", classBytes("shared.AlsoFirst"));
        write(second, "shared/Second.class", classBytes("shared.Second"));

        JavaPackage javaPackage = importPackages(List.of(first, second))
                .find("shared").orElseThrow();

        assertTrue(javaPackage.isSplitAcrossOrigins());
        assertEquals(2, javaPackage.origins().size());
        assertEquals(List.of(0, 1), javaPackage.origins().stream()
                .map(JavaPackageOrigin::precedence).toList());
        assertEquals(List.of("first", "second"), javaPackage.origins().stream()
                .map(JavaPackageOrigin::container).toList());
        assertEquals(List.of("shared.AlsoFirst", "shared.First", "shared.Second"),
                javaPackage.types().stream().map(JavaType::binaryName).sorted().toList());
    }

    @Test
    void typeModelResultProvidesTheSameDeterministicPackageIndex() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        write(first, "b/B.class", classBytes("b.B"));
        write(first, "a/A.class", classBytes("a.A"));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(first)))
                .resources();
        TypeModelResult result = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));

        assertEquals(List.of("a", "b"), result.packages().all().stream()
                .map(value -> value.name().value()).toList());
    }

    private JavaPackageIndex importPackages(List<Path> roots) {
        var inputs = roots.stream().map(ClassFileInput::directory).toList();
        var resources = new ClassFileInputEnumerator().enumerate(inputs).resources();
        List<JavaType> types = new TypeModelBuilder()
                .build(new ClassFileReader().readAll(resources)).types();
        return JavaPackageIndex.of(types);
    }

    private static byte[] classBytes(String binaryName) {
        return ClassFile.of().build(ClassDesc.of(binaryName), ignored -> {});
    }

    private static void write(Path root, String resource, byte[] bytes) throws IOException {
        Path file = root.resolve(resource);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }
}
