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
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilerArtifactModelTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc GENERATED = ClassDesc.of("tools.GeneratedMarker");

    @TempDir Path temporaryDirectory;

    @Test
    void retainsSyntheticBridgeMandatedParameterAndAccessProvenance() throws IOException {
        JavaType type = importType("generated.AdapterGenerated", compilerArtifactClass());
        JavaMember bridge = type.declaredMembers().getFirst();
        JavaParameter parameter = bridge.parameters().getFirst();
        JavaCodeAccess access = bridge.codeAccesses().getFirst();

        assertTrue(type.modifiers().contains(JavaModifier.SYNTHETIC));
        assertTrue(bridge.modifiers().containsAll(
                Set.of(JavaMemberModifier.BRIDGE, JavaMemberModifier.SYNTHETIC)));
        assertEquals(Optional.of("value"), parameter.name());
        assertTrue(parameter.modifiers().containsAll(Set.of(
                JavaParameterModifier.FINAL,
                JavaParameterModifier.SYNTHETIC,
                JavaParameterModifier.MANDATED)));
        assertTrue(access.artifactProvenance().syntheticType());
        assertTrue(access.artifactProvenance().syntheticMember());
        assertTrue(access.artifactProvenance().bridgeMember());
    }

    @Test
    void sourcePresentationCannotDeleteDependencyEvidence() throws IOException {
        JavaType type = importType("generated.AdapterGenerated", compilerArtifactClass());

        CompilerArtifactView view = new CompilerArtifactProjector().project(
                List.of(type),
                CompilerArtifactFilterOptions.sourceView(GeneratedCodeOptions.disabled()));

        assertTrue(view.presentedTypes().isEmpty());
        assertTrue(view.presentedMembers().isEmpty());
        assertEquals(1, view.dependencyEvidence().size());
        assertEquals("target.Api", ((JvmReferenceType) view.dependencyEvidence().getFirst()
                .target().ownerType()).binaryName());
    }

    @Test
    void generatedClassificationIsOptInAndNeverUsesOneAnnotationAlone() throws IOException {
        JavaType ordinaryName = importAnnotated("sample.Regular");
        JavaType generatedName = importAnnotated("sample.Generated");
        GeneratedCodeClassifier classifier = new GeneratedCodeClassifier();
        GeneratedCodeOptions enabled = GeneratedCodeOptions.enabled(Set.of("tools.GeneratedMarker"));

        assertFalse(classifier.classify(ordinaryName, GeneratedCodeOptions.disabled()).generated());
        GeneratedCodeClassification annotationOnly = classifier.classify(ordinaryName, enabled);
        assertFalse(annotationOnly.generated());
        assertEquals(List.of(GeneratedCodeSignal.CONFIGURED_ANNOTATION), annotationOnly.signals());
        assertTrue(classifier.classify(generatedName, enabled).generated());
    }

    private byte[] compilerArtifactClass() {
        return ClassFile.of().build(ClassDesc.of("generated.AdapterGenerated"), builder -> builder
                .withFlags(AccessFlag.PUBLIC, AccessFlag.SYNTHETIC)
                .withMethod(
                        "adapt",
                        MethodTypeDesc.of(VOID, INT),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC
                                | ClassFile.ACC_BRIDGE | ClassFile.ACC_SYNTHETIC,
                        method -> method
                                .with(MethodParametersAttribute.of(MethodParameterInfo.of(
                                        Optional.of("value"),
                                        AccessFlag.FINAL,
                                        AccessFlag.SYNTHETIC,
                                        AccessFlag.MANDATED)))
                                .withCode(code -> code
                                        .invokestatic(
                                                ClassDesc.of("target.Api"),
                                                "touch",
                                                MethodTypeDesc.of(VOID))
                                        .return_())));
    }

    private JavaType importAnnotated(String binaryName) throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(GENERATED))));
        return importType(binaryName, bytes);
    }

    private JavaType importType(String binaryName, byte[] bytes) throws IOException {
        Path root = Files.createTempDirectory(temporaryDirectory, "case-");
        String resourceName = binaryName.replace('.', '/') + ".class";
        Path file = root.resolve(resourceName);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        var resource = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(root)))
                .resources().getFirst();
        return new TypeModelBuilder().build(new ClassFileReader().read(resource))
                .types().getFirst();
    }
}
