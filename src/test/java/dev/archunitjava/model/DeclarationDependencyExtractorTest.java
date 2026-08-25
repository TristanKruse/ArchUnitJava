package dev.archunitjava.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileOrigin;
import dev.archunitjava.importer.ClassFileReadResult;
import dev.archunitjava.importer.ParsedAnnotation;
import dev.archunitjava.importer.ParsedAnnotationDefault;
import dev.archunitjava.importer.ParsedAnnotationElement;
import dev.archunitjava.importer.ParsedAnnotationOccurrence;
import dev.archunitjava.importer.ParsedAnnotationValue;
import dev.archunitjava.importer.ParsedClassFile;
import dev.archunitjava.importer.ParsedMember;
import dev.archunitjava.importer.ParsedNestingMetadata;
import dev.archunitjava.importer.ParsedRecordComponent;
import java.lang.classfile.ClassFile;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class DeclarationDependencyExtractorTest {
    @Test
    void extractsEveryDeclarationSourceWithItsTypedOwnerAndRawValue() {
        DeclarationDependencyResult result = extract(subject());

        DeclarationDependency field = dependency(
                result, "java.util.List", DeclarationDependencyEvidenceKind.ERASED);
        DeclarationDependencySource fieldSource = field.sources().stream()
                .filter(source -> source.kind() == DeclarationDependencySourceKind.FIELD_DESCRIPTOR)
                .findFirst().orElseThrow();
        assertInstanceOf(DeclarationDependencyOwner.MemberOwner.class, fieldSource.owner());
        assertEquals("Ljava/util/List;", fieldSource.classFileValue());

        DeclarationDependency record = dependency(
                result, "external.RecordValue", DeclarationDependencyEvidenceKind.ERASED);
        assertInstanceOf(DeclarationDependencyOwner.RecordComponentOwner.class,
                record.sources().getFirst().owner());
        assertEquals(DeclarationDependencySourceKind.RECORD_COMPONENT_DESCRIPTOR,
                record.sources().getFirst().kind());

        assertEquals(DeclarationDependencySourceKind.SUPERCLASS,
                dependency(result, "external.Base", DeclarationDependencyEvidenceKind.DECLARED_RELATIONSHIP)
                        .sources().getFirst().kind());
        assertEquals(DeclarationDependencySourceKind.INTERFACE,
                dependency(result, "api.Contract", DeclarationDependencyEvidenceKind.DECLARED_RELATIONSHIP)
                        .sources().getFirst().kind());
        assertEquals(DeclarationDependencySourceKind.PERMITTED_SUBCLASS,
                dependency(result, "sample.Allowed", DeclarationDependencyEvidenceKind.DECLARED_RELATIONSHIP)
                        .sources().getFirst().kind());
    }

    @Test
    void erasedAndGenericOnlyEvidenceRemainSeparate() {
        DeclarationDependencyResult result = extract(subject());

        assertEquals(DeclarationDependencyEvidenceKind.ERASED,
                dependency(result, "java.util.List", DeclarationDependencyEvidenceKind.ERASED)
                        .evidenceKind());
        assertEquals(DeclarationDependencySourceKind.FIELD_GENERIC_SIGNATURE,
                dependency(result, "external.GenericField", DeclarationDependencyEvidenceKind.GENERIC_ONLY)
                        .sources().getFirst().kind());
        assertEquals(DeclarationDependencySourceKind.METHOD_GENERIC_SIGNATURE,
                dependency(result, "java.lang.Number", DeclarationDependencyEvidenceKind.GENERIC_ONLY)
                        .sources().getFirst().kind());
        assertEquals(DeclarationDependencySourceKind.RECORD_COMPONENT_GENERIC_SIGNATURE,
                dependency(result, "external.RecordGeneric", DeclarationDependencyEvidenceKind.GENERIC_ONLY)
                        .sources().getFirst().kind());
        assertEquals(DeclarationDependencySourceKind.CLASS_GENERIC_SIGNATURE,
                dependency(result, "java.lang.Object", DeclarationDependencyEvidenceKind.GENERIC_ONLY)
                        .sources().getFirst().kind());
    }

    @Test
    void annotationTypesAndNestedValuesRetainTheirExactSites() {
        DeclarationDependencyResult result = extract(subject());

        DeclarationDependency marker = dependency(
                result, "ann.Marker", DeclarationDependencyEvidenceKind.ANNOTATION);
        DeclarationDependency annotated = dependency(
                result, "external.Annotated", DeclarationDependencyEvidenceKind.ANNOTATION);
        DeclarationDependency mode = dependency(
                result, "ann.Mode", DeclarationDependencyEvidenceKind.ANNOTATION);
        DeclarationDependency nested = dependency(
                result, "ann.Nested", DeclarationDependencyEvidenceKind.ANNOTATION);

        assertTrue(marker.sources().getFirst().annotationSite().isPresent());
        assertEquals(AnnotationSiteKind.TYPE_DECLARATION,
                marker.sources().getFirst().annotationSite().orElseThrow().kind());
        assertEquals("[Lexternal/Annotated;", annotated.sources().getFirst().classFileValue());
        assertEquals("Lann/Mode;", mode.sources().getFirst().classFileValue());
        assertEquals(DeclarationDependencySourceKind.ANNOTATION_VALUE,
                nested.sources().getFirst().kind());
    }

    @Test
    void selfDuplicatesPrimitivesArraysAndExternalTargetsAreExplicit() {
        DeclarationDependencyResult result = extract(subject());
        DeclarationDependency self = dependency(
                result, "sample.Subject", DeclarationDependencyEvidenceKind.ERASED);
        DeclarationDependency array = dependency(
                result, "external.ArrayValue", DeclarationDependencyEvidenceKind.ERASED);

        assertTrue(self.selfDependency());
        assertFalse(self.externalTarget());
        assertEquals(2, array.sources().getFirst().arrayDimensions());
        assertTrue(array.externalTarget());
        assertTrue(result.primitiveReferencesIgnored() >= 2);
        assertEquals(4, result.duplicateSourcesCollapsed());
        assertTrue(result.externalTargets().stream()
                .map(JavaTypeName::binaryName)
                .toList().contains("external.ArrayValue"));
        assertFalse(result.externalTargets().stream()
                .anyMatch(type -> type.binaryName().equals("sample.Subject")));
    }

    private static DeclarationDependency dependency(
            DeclarationDependencyResult result,
            String target,
            DeclarationDependencyEvidenceKind evidenceKind) {
        return result.dependencies().stream()
                .filter(value -> value.target().binaryName().equals(target)
                        && value.evidenceKind() == evidenceKind)
                .findFirst().orElseThrow();
    }

    private static DeclarationDependencyResult extract(ParsedClassFile parsed) {
        JavaType type = new TypeModelBuilder()
                .build(new ClassFileReadResult(List.of(parsed), List.of()))
                .types().getFirst();
        return new DeclarationDependencyExtractor().extract(List.of(type));
    }

    private static ParsedClassFile subject() {
        ParsedAnnotation nested = new ParsedAnnotation("Lann/Nested;", List.of());
        ParsedAnnotation annotation = new ParsedAnnotation(
                "Lann/Marker;",
                List.of(
                        new ParsedAnnotationElement(
                                "classes",
                                new ParsedAnnotationValue.ArrayValue(List.of(
                                        new ParsedAnnotationValue.ClassValue("[Lexternal/Annotated;")))),
                        new ParsedAnnotationElement(
                                "mode", new ParsedAnnotationValue.EnumValue("Lann/Mode;", "FAST")),
                        new ParsedAnnotationElement(
                                "nested", new ParsedAnnotationValue.NestedAnnotationValue(nested))));
        ParsedAnnotationOccurrence occurrence = new ParsedAnnotationOccurrence(
                ParsedAnnotationOccurrence.Visibility.RUNTIME_VISIBLE,
                ParsedAnnotationOccurrence.Container.TYPE,
                "",
                "",
                ParsedAnnotationOccurrence.Site.DECLARATION,
                OptionalInt.empty(),
                Optional.empty(),
                annotation);
        List<ParsedMember> members = List.of(
                new ParsedMember(
                        ParsedMember.Kind.FIELD,
                        "array",
                        "[[Lexternal/ArrayValue;",
                        0,
                        false),
                new ParsedMember(
                        ParsedMember.Kind.FIELD,
                        "count",
                        "I",
                        0,
                        false),
                new ParsedMember(
                        ParsedMember.Kind.FIELD,
                        "names",
                        "Ljava/util/List;",
                        0,
                        false,
                        List.of(),
                        Optional.of("Ljava/util/List<Lexternal/GenericField;>;")),
                new ParsedMember(
                        ParsedMember.Kind.FIELD,
                        "self",
                        "Lsample/Subject;",
                        0,
                        false),
                new ParsedMember(
                        ParsedMember.Kind.METHOD,
                        "convert",
                        "(Lexternal/Param;[I)Lexternal/Return;",
                        ClassFile.ACC_ABSTRACT,
                        false,
                        List.of(),
                        Optional.of(
                                "<X:Ljava/lang/Number;>(Lexternal/Param;[I)Lexternal/Return;")));
        return new ParsedClassFile(
                "sample.Subject",
                ClassFile.ACC_PUBLIC,
                ClassFile.JAVA_25_VERSION,
                0,
                false,
                "sample/Subject.class",
                new ClassFileOrigin(
                        ClassFileInput.Kind.DIRECTORY, "test-classes", "sample/Subject.class"),
                0,
                Optional.of("external.Base"),
                List.of("api.Contract"),
                Optional.empty(),
                members,
                List.of(occurrence, occurrence),
                List.<ParsedAnnotationDefault>of(),
                Optional.of(
                        "<T:Ljava/lang/Object;>Lexternal/Base;Lapi/Contract;"),
                true,
                List.of(new ParsedRecordComponent(
                        "value",
                        "Lexternal/RecordValue;",
                        Optional.of(
                                "Ljava/util/List<Lexternal/RecordGeneric;>;"))),
                true,
                List.of("sample.Allowed"),
                ParsedNestingMetadata.empty());
    }
}
