package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.AnnotationVisibility;
import dev.archunitjava.model.JavaAnnotationValue;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.AnnotationQuery;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnnotationRulesTest {
    private static final ClassDesc MARKER = ClassDesc.of("ann.Marker");
    private static final ClassDesc CONFIG = ClassDesc.of("ann.Config");
    private static final ClassDesc ROLE = ClassDesc.of("ann.Role");
    private static final ClassDesc INHERITED_MARKER = ClassDesc.of("ann.InheritedMarker");
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importFixture() throws IOException {
        write("ann/Marker.class", annotationClass("ann.Marker", ignored -> {}));
        write("ann/Config.class", annotationClass("ann.Config", ignored -> {}));
        write("ann/Role.class", annotationClass("ann.Role", builder -> builder
                .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(MARKER)))));
        write("ann/InheritedMarker.class", annotationClass(
                "ann.InheritedMarker", builder -> builder
                        .with(RuntimeVisibleAnnotationsAttribute.of(
                                Annotation.of(ClassDesc.of("java.lang.annotation.Inherited"))))));
        write("sample/Parent.class", ClassFile.of().build(
                ClassDesc.of("sample.Parent"), builder -> builder
                        .with(RuntimeVisibleAnnotationsAttribute.of(
                                Annotation.of(INHERITED_MARKER)))));
        write("sample/Child.class", ClassFile.of().build(
                ClassDesc.of("sample.Child"), builder -> builder
                        .withSuperclass(ClassDesc.of("sample.Parent"))));

        Annotation config = Annotation.of(
                CONFIG, AnnotationElement.ofInt("level", 2));
        TypeAnnotation fieldTypeUse = TypeAnnotation.of(
                TypeAnnotation.TargetInfo.ofField(),
                List.of(TypeAnnotation.TypePathComponent.ARRAY),
                Annotation.of(MARKER));
        write("sample/Subject.class", ClassFile.of().build(
                ClassDesc.of("sample.Subject"), builder -> builder
                        .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT)
                        .with(RuntimeVisibleAnnotationsAttribute.of(
                                config,
                                Annotation.of(ROLE),
                                Annotation.of(ClassDesc.of("external.UnknownCarrier"))))
                        .with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(MARKER)))
                        .withField("names", ClassDesc.ofDescriptor("[Ljava/lang/String;"), field -> field
                                .with(RuntimeInvisibleTypeAnnotationsAttribute.of(fieldTypeUse)))
                        .withMethod("call", MethodTypeDesc.of(VOID, INT),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                                method -> method.with(
                                        RuntimeVisibleParameterAnnotationsAttribute.of(
                                                List.of(List.of(config)))))));
        write("sample/package-info.class", ClassFile.of().build(
                ClassDesc.of("sample.package-info"), builder -> builder
                        .with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(MARKER)))));

        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void directVisibilityAndTypedValueConditionsAreExplicit() {
        AnnotationRuleSpec levelTwo = AnnotationRuleSpec.require(
                AnnotationQuery.direct("ann.Config")
                        .withVisibility(AnnotationVisibility.RUNTIME_VISIBLE))
                .withValueCondition(AnnotationValueCondition.equalTo(
                        "level", integer(2)));
        AnnotationRuleSpec levelThree = levelTwo.withValueCondition(
                AnnotationValueCondition.equalTo("level", integer(3)));

        assertEquals(RuleStatus.PASSED,
                AnnotationRules.types(model, binary("sample.Subject"), levelTwo)
                        .check().status());
        assertEquals(RuleStatus.FAILED,
                AnnotationRules.types(model, binary("sample.Subject"), levelThree)
                        .check().status());

        var invisible = AnnotationRules.types(
                model,
                binary("sample.Subject"),
                AnnotationRuleSpec.forbid(AnnotationQuery.direct("ann.Marker")
                        .withVisibility(AnnotationVisibility.RUNTIME_INVISIBLE))).check();
        assertEquals(RuleStatus.FAILED, invisible.status());
        assertEquals("TYPE_DECLARATION",
                invisible.violations().getFirst().attributes().get("siteKind"));
        assertEquals("RUNTIME_INVISIBLE",
                invisible.violations().getFirst().attributes().get("visibility"));
        assertEquals(RuleStatus.PASSED, AnnotationRules.types(
                model,
                binary("sample.Subject"),
                AnnotationRuleSpec.forbid(AnnotationQuery.direct("ann.Marker")
                        .withVisibility(AnnotationVisibility.RUNTIME_VISIBLE))).check().status());
    }

    @Test
    void metaAndTrueJavaInheritedMatchingRetainPathsAndRejectUnknownSilence() {
        var meta = AnnotationRules.types(
                model,
                binary("sample.Subject"),
                AnnotationRuleSpec.forbid(
                        AnnotationQuery.metaAnnotated("ann.Marker"))).check();
        var inherited = AnnotationRules.types(
                model,
                binary("sample.Child"),
                AnnotationRuleSpec.forbid(
                        AnnotationQuery.inherited("ann.InheritedMarker"))).check();

        assertEquals(RuleStatus.FAILED, meta.status());
        assertEquals("ann.Role -> ann.Marker",
                meta.violations().getFirst().attributes().get("annotationPath"));
        assertEquals(RuleStatus.FAILED, inherited.status());
        assertEquals("sample.Child -> sample.Parent",
                inherited.violations().getFirst().attributes().get("annotationPath"));
        assertFalse(inherited.violations().getFirst().evidence().isEmpty());

        var unknown = AnnotationRules.types(
                model,
                binary("sample.Subject"),
                AnnotationRuleSpec.require(
                        AnnotationQuery.metaAnnotated("ann.NeverPresent"))).check();
        assertEquals(RuleStatus.INCOMPLETE, unknown.status());
        assertEquals("rule.annotation.unknown", unknown.diagnostics().getFirst().code());
    }

    @Test
    void parameterPackageAndTypeUsePlacementRemainsExact() {
        AnnotationRuleSpec parameterConfig = AnnotationRuleSpec.require(
                AnnotationQuery.direct("ann.Config"))
                .withValueCondition(AnnotationValueCondition.equalTo(
                        "level", integer(2)));
        assertEquals(RuleStatus.PASSED, AnnotationRules.parameters(
                model, MemberSelector.named("call"), parameterConfig).check().status());

        var parameterViolation = AnnotationRules.parameters(
                model,
                MemberSelector.named("call"),
                AnnotationRuleSpec.forbid(AnnotationQuery.direct("ann.Config"))).check();
        assertEquals(RuleStatus.FAILED, parameterViolation.status());
        assertEquals("PARAMETER",
                parameterViolation.violations().getFirst().attributes().get("siteKind"));
        assertEquals("0",
                parameterViolation.violations().getFirst().attributes().get("parameterIndex"));

        assertEquals(RuleStatus.PASSED, AnnotationRules.packages(
                model,
                PackageSelector.name(exact("sample")),
                AnnotationRuleSpec.require(AnnotationQuery.direct("ann.Marker")
                        .withVisibility(AnnotationVisibility.RUNTIME_INVISIBLE))).check().status());

        var typeUse = AnnotationRules.members(
                model,
                MemberSelector.named("names"),
                AnnotationRuleSpec.forbid(AnnotationQuery.typeUse("ann.Marker")
                        .withVisibility(AnnotationVisibility.RUNTIME_INVISIBLE))).check();
        assertEquals(RuleStatus.FAILED, typeUse.status());
        assertEquals("TYPE_USE", typeUse.violations().getFirst().attributes().get("siteKind"));
        assertEquals("FIELD", typeUse.violations().getFirst().attributes().get("typeUseTarget"));
        assertEquals("ARRAY:0", typeUse.violations().getFirst().attributes().get("typeUsePath"));
    }

    private static JavaAnnotationValue integer(int value) {
        return new JavaAnnotationValue.ScalarValue(
                JavaAnnotationValue.ScalarKind.INT, Integer.toString(value));
    }

    private static TypeSelector binary(String binaryName) {
        return TypeSelector.binaryName(exact(binaryName));
    }

    private static JavaPattern exact(String value) {
        return JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value);
    }

    private static byte[] annotationClass(
            String binaryName, Consumer<java.lang.classfile.ClassBuilder> customizer) {
        return ClassFile.of().build(ClassDesc.of(binaryName), builder -> {
            builder.withFlags(
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE
                            | ClassFile.ACC_ABSTRACT | ClassFile.ACC_ANNOTATION);
            customizer.accept(builder);
        });
    }

    private void write(String resourceName, byte[] bytes) throws IOException {
        Path target = temporaryDirectory.resolve(resourceName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
