package dev.archunitjava.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Extracts declaration-only type dependencies without loading target classes. */
public final class DeclarationDependencyExtractor {
    public DeclarationDependencyResult extract(Collection<JavaType> types) {
        Objects.requireNonNull(types, "types");
        List<JavaType> imported = types.stream()
                .map(value -> Objects.requireNonNull(value, "type"))
                .sorted()
                .toList();
        Set<String> importedNames = imported.stream()
                .map(JavaType::binaryName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Accumulator output = new Accumulator(importedNames);
        imported.forEach(type -> extract(type, output));
        return output.result();
    }

    private static void extract(JavaType type, Accumulator output) {
        DeclarationDependencyOwner typeOwner = new DeclarationDependencyOwner.TypeOwner(type.name());
        type.superclass().ifPresent(superclass -> output.add(
                type.name(),
                superclass,
                DeclarationDependencyEvidenceKind.DECLARED_RELATIONSHIP,
                source(
                        DeclarationDependencySourceKind.SUPERCLASS,
                        typeOwner,
                        superclass.binaryName(),
                        "",
                        0,
                        Optional.empty(),
                        type.location())));
        for (int index = 0; index < type.directInterfaces().size(); index++) {
            JvmReferenceType interfaceType = type.directInterfaces().get(index);
            output.add(
                    type.name(),
                    interfaceType,
                    DeclarationDependencyEvidenceKind.DECLARED_RELATIONSHIP,
                    source(
                            DeclarationDependencySourceKind.INTERFACE,
                            typeOwner,
                            interfaceType.binaryName(),
                            "index=" + index,
                            0,
                            Optional.empty(),
                            type.location()));
        }
        for (int index = 0; index < type.permittedSubclasses().size(); index++) {
            JvmReferenceType permitted = type.permittedSubclasses().get(index);
            output.add(
                    type.name(),
                    permitted,
                    DeclarationDependencyEvidenceKind.DECLARED_RELATIONSHIP,
                    source(
                            DeclarationDependencySourceKind.PERMITTED_SUBCLASS,
                            typeOwner,
                            permitted.binaryName(),
                            "index=" + index,
                            0,
                            Optional.empty(),
                            type.location()));
        }
        genericOnly(
                type.name(),
                typeOwner,
                type.genericView().declaredSignature(),
                type.genericView().referencedTypes(TypeReferenceEvidence.ERASED),
                type.genericView().referencedTypes(TypeReferenceEvidence.GENERIC),
                DeclarationDependencySourceKind.CLASS_GENERIC_SIGNATURE,
                type.location(),
                output);
        annotations(type.name(), typeOwner, type.annotations(), type.location(), output);

        for (JavaMember member : type.declaredMembers()) {
            DeclarationDependencyOwner owner = new DeclarationDependencyOwner.MemberOwner(member.signature());
            if (member.kind() == JavaMemberKind.FIELD) {
                output.addType(
                        type.name(),
                        member.fieldType(),
                        DeclarationDependencyEvidenceKind.ERASED,
                        DeclarationDependencySourceKind.FIELD_DESCRIPTOR,
                        owner,
                        member.descriptor(),
                        "",
                        Optional.empty(),
                        member.location());
                genericOnly(
                        type.name(),
                        owner,
                        member.genericFieldView().declaredSignature(),
                        member.genericFieldView().referencedTypes(TypeReferenceEvidence.ERASED),
                        member.genericFieldView().referencedTypes(TypeReferenceEvidence.GENERIC),
                        DeclarationDependencySourceKind.FIELD_GENERIC_SIGNATURE,
                        member.location(),
                        output);
            } else {
                JvmMethodType method = member.methodType();
                for (int index = 0; index < method.parameterTypes().size(); index++) {
                    output.addType(
                            type.name(),
                            method.parameterTypes().get(index),
                            DeclarationDependencyEvidenceKind.ERASED,
                            DeclarationDependencySourceKind.METHOD_PARAMETER_DESCRIPTOR,
                            owner,
                            member.descriptor(),
                            "parameter=" + index,
                            Optional.empty(),
                            member.location());
                }
                output.addType(
                        type.name(),
                        method.returnType(),
                        DeclarationDependencyEvidenceKind.ERASED,
                        DeclarationDependencySourceKind.METHOD_RETURN_DESCRIPTOR,
                        owner,
                        member.descriptor(),
                        "return",
                        Optional.empty(),
                        member.location());
                genericOnly(
                        type.name(),
                        owner,
                        member.genericMethodView().declaredSignature(),
                        member.genericMethodView().referencedTypes(TypeReferenceEvidence.ERASED),
                        member.genericMethodView().referencedTypes(TypeReferenceEvidence.GENERIC),
                        DeclarationDependencySourceKind.METHOD_GENERIC_SIGNATURE,
                        member.location(),
                        output);
            }
            annotations(type.name(), owner, member.annotations(), member.location(), output);
            member.annotationDefault().ifPresent(value -> annotationValue(
                    type.name(),
                    owner,
                    value,
                    DeclarationDependencySourceKind.ANNOTATION_DEFAULT_VALUE,
                    "default",
                    Optional.empty(),
                    member.location(),
                    output));
        }

        for (JavaRecordComponent component : type.recordComponents()) {
            DeclarationDependencyOwner owner = new DeclarationDependencyOwner.RecordComponentOwner(
                    type.name(), component.name(), component.descriptor());
            output.addType(
                    type.name(),
                    component.type(),
                    DeclarationDependencyEvidenceKind.ERASED,
                    DeclarationDependencySourceKind.RECORD_COMPONENT_DESCRIPTOR,
                    owner,
                    component.descriptor(),
                    "",
                    Optional.empty(),
                    component.location());
            genericOnly(
                    type.name(),
                    owner,
                    component.typeView().declaredSignature(),
                    component.typeView().referencedTypes(TypeReferenceEvidence.ERASED),
                    component.typeView().referencedTypes(TypeReferenceEvidence.GENERIC),
                    DeclarationDependencySourceKind.RECORD_COMPONENT_GENERIC_SIGNATURE,
                    component.location(),
                    output);
            annotations(type.name(), owner, component.annotations(), component.location(), output);
        }
    }

    private static void genericOnly(
            JavaTypeName origin,
            DeclarationDependencyOwner owner,
            Optional<String> signature,
            List<JvmReferenceType> erased,
            List<JvmReferenceType> generic,
            DeclarationDependencySourceKind sourceKind,
            DeclarationLocation location,
            Accumulator output) {
        if (signature.isEmpty()) return;
        Set<String> erasedNames = erased.stream()
                .map(JvmReferenceType::binaryName)
                .collect(java.util.stream.Collectors.toSet());
        generic.stream()
                .filter(type -> !erasedNames.contains(type.binaryName()))
                .forEach(target -> output.add(
                        origin,
                        target,
                        DeclarationDependencyEvidenceKind.GENERIC_ONLY,
                        source(
                                sourceKind,
                                owner,
                                signature.orElseThrow(),
                                "generic-only",
                                0,
                                Optional.empty(),
                                location)));
    }

    private static void annotations(
            JavaTypeName origin,
            DeclarationDependencyOwner owner,
            List<JavaAnnotationOccurrence> annotations,
            DeclarationLocation location,
            Accumulator output) {
        for (JavaAnnotationOccurrence occurrence : annotations) {
            JavaAnnotation annotation = occurrence.annotation();
            output.add(
                    origin,
                    annotation.type(),
                    DeclarationDependencyEvidenceKind.ANNOTATION,
                    source(
                            DeclarationDependencySourceKind.ANNOTATION_TYPE,
                            owner,
                            annotation.type().descriptor(),
                            occurrence.site().stableKey(),
                            0,
                            Optional.of(occurrence.site()),
                            location));
            for (JavaAnnotationElement element : annotation.elements()) {
                annotationValue(
                        origin,
                        owner,
                        element.value(),
                        DeclarationDependencySourceKind.ANNOTATION_VALUE,
                        "element=" + element.name(),
                        Optional.of(occurrence.site()),
                        location,
                        output);
            }
        }
    }

    private static void annotationValue(
            JavaTypeName origin,
            DeclarationDependencyOwner owner,
            JavaAnnotationValue value,
            DeclarationDependencySourceKind sourceKind,
            String path,
            Optional<AnnotationSite> site,
            DeclarationLocation location,
            Accumulator output) {
        if (value instanceof JavaAnnotationValue.EnumValue enumValue) {
            output.add(
                    origin,
                    enumValue.enumType(),
                    DeclarationDependencyEvidenceKind.ANNOTATION,
                    source(sourceKind, owner, enumValue.enumType().descriptor(), path, 0, site, location));
        } else if (value instanceof JavaAnnotationValue.ClassValue classValue) {
            JvmType type = classValue.descriptor().equals("V")
                    ? JvmVoidType.VOID
                    : JvmDescriptors.parseField(classValue.descriptor());
            output.addType(
                    origin,
                    type,
                    DeclarationDependencyEvidenceKind.ANNOTATION,
                    sourceKind,
                    owner,
                    classValue.descriptor(),
                    path,
                    site,
                    location);
        } else if (value instanceof JavaAnnotationValue.NestedAnnotationValue nested) {
            JavaAnnotation annotation = nested.annotation();
            output.add(
                    origin,
                    annotation.type(),
                    DeclarationDependencyEvidenceKind.ANNOTATION,
                    source(sourceKind, owner, annotation.type().descriptor(), path + "/annotation", 0, site, location));
            for (JavaAnnotationElement element : annotation.elements()) {
                annotationValue(
                        origin,
                        owner,
                        element.value(),
                        sourceKind,
                        path + "/" + element.name(),
                        site,
                        location,
                        output);
            }
        } else if (value instanceof JavaAnnotationValue.ArrayValue array) {
            for (int index = 0; index < array.values().size(); index++) {
                annotationValue(
                        origin,
                        owner,
                        array.values().get(index),
                        sourceKind,
                        path + "[" + index + "]",
                        site,
                        location,
                        output);
            }
        }
    }

    private static DeclarationDependencySource source(
            DeclarationDependencySourceKind kind,
            DeclarationDependencyOwner owner,
            String classFileValue,
            String detail,
            int dimensions,
            Optional<AnnotationSite> annotationSite,
            DeclarationLocation location) {
        return new DeclarationDependencySource(
                kind, owner, classFileValue, detail, dimensions, annotationSite, location);
    }

    private record Key(
            JavaTypeName origin,
            JvmReferenceType target,
            DeclarationDependencyEvidenceKind evidenceKind)
            implements Comparable<Key> {
        @Override
        public int compareTo(Key other) {
            int result = origin.compareTo(other.origin);
            if (result != 0) return result;
            result = target.binaryName().compareTo(other.target.binaryName());
            return result != 0 ? result : evidenceKind.compareTo(other.evidenceKind);
        }
    }

    private static final class Accumulator {
        private final Set<String> importedNames;
        private final Map<Key, TreeSet<DeclarationDependencySource>> dependencies = new TreeMap<>();
        private final TreeSet<JavaTypeName> externalTargets = new TreeSet<>();
        private int primitiveReferencesIgnored;
        private int duplicateSourcesCollapsed;

        private Accumulator(Set<String> importedNames) {
            this.importedNames = importedNames;
        }

        private void addType(
                JavaTypeName origin,
                JvmType type,
                DeclarationDependencyEvidenceKind evidenceKind,
                DeclarationDependencySourceKind sourceKind,
                DeclarationDependencyOwner owner,
                String classFileValue,
                String detail,
                Optional<AnnotationSite> site,
                DeclarationLocation location) {
            int dimensions = type instanceof JvmArrayType array ? array.dimensions() : 0;
            JvmType element = type instanceof JvmArrayType array ? array.elementType() : type;
            if (element instanceof JvmReferenceType reference) {
                add(
                        origin,
                        reference,
                        evidenceKind,
                        source(sourceKind, owner, classFileValue, detail, dimensions, site, location));
            } else {
                primitiveReferencesIgnored++;
            }
        }

        private void add(
                JavaTypeName origin,
                JvmReferenceType target,
                DeclarationDependencyEvidenceKind evidenceKind,
                DeclarationDependencySource source) {
            Key key = new Key(origin, target, evidenceKind);
            if (!dependencies.computeIfAbsent(key, ignored -> new TreeSet<>()).add(source)) {
                duplicateSourcesCollapsed++;
            }
            if (!importedNames.contains(target.binaryName())) {
                externalTargets.add(new JavaTypeName(target.binaryName()));
            }
        }

        private DeclarationDependencyResult result() {
            List<DeclarationDependency> values = dependencies.entrySet().stream()
                    .map(entry -> new DeclarationDependency(
                            entry.getKey().origin,
                            entry.getKey().target,
                            entry.getKey().evidenceKind,
                            entry.getKey().origin.binaryName().equals(entry.getKey().target.binaryName()),
                            !importedNames.contains(entry.getKey().target.binaryName()),
                            List.copyOf(entry.getValue())))
                    .toList();
            return new DeclarationDependencyResult(
                    values,
                    List.copyOf(externalTargets),
                    primitiveReferencesIgnored,
                    duplicateSourcesCollapsed);
        }
    }
}
