package dev.archunitjava.cli;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.DeclarationDependencyEvidenceKind;
import dev.archunitjava.model.DeclarationDependencyExtractor;
import dev.archunitjava.model.DeclarationDependencyOwner;
import dev.archunitjava.model.DeclarationDependencySource;
import dev.archunitjava.model.DeclarationDependencySourceKind;
import dev.archunitjava.model.JavaCodeAccessKind;
import dev.archunitjava.model.JavaMemberSignature;
import dev.archunitjava.model.JvmArrayType;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.JvmType;
import dev.archunitjava.model.TypeModelResult;
import java.util.Optional;
import java.util.OptionalInt;

/** Builds the CLI graph from library-owned static model values without loading target classes. */
final class CliGraphBuilder {
    private CliGraphBuilder() {}

    static DependencyGraph build(TypeModelResult model) {
        DependencyGraph.Builder graph = DependencyGraph.builder();
        model.types().forEach(type -> graph.addNode(TypeId.ofBinaryName(type.binaryName())));
        var declarations = new DeclarationDependencyExtractor().extract(model.types());
        declarations.dependencies().forEach(dependency -> {
            TypeId origin = TypeId.ofBinaryName(dependency.origin().binaryName());
            TypeId target = TypeId.ofBinaryName(dependency.target().binaryName());
            graph.addNode(origin).addNode(target);
            dependency.sources().forEach(source -> graph.addDependency(
                    origin, target,
                    declarationKind(source.kind(), dependency.evidenceKind()),
                    declarationEvidence(source)));
        });
        model.types().forEach(type -> type.declaredMembers().forEach(member ->
                member.codeAccesses().forEach(access -> targetType(access.target().ownerType())
                        .ifPresent(targetName -> {
                            TypeId origin = TypeId.ofBinaryName(type.binaryName());
                            TypeId target = TypeId.ofBinaryName(targetName);
                            graph.addNode(origin).addNode(target);
                            JavaMemberSignature signature = member.signature();
                            MemberId owner = MemberId.of(
                                    origin, signature.name(), signature.descriptor());
                            graph.addDependency(
                                    origin,
                                    target,
                                    accessKind(access.kind()),
                                    access.location().dependencyEvidence(owner));
                        }))));
        return graph.build();
    }

    private static DependencyEvidence declarationEvidence(DeclarationDependencySource source) {
        Optional<MemberId> owner = source.owner() instanceof DeclarationDependencyOwner.MemberOwner member
                ? Optional.of(MemberId.of(
                        TypeId.ofBinaryName(member.member().owner().binaryName()),
                        member.member().name(), member.member().descriptor()))
                : Optional.empty();
        return new DependencyEvidence(
                source.location().resource().locationId(),
                owner,
                OptionalInt.empty(),
                source.location().sourceFile().map(value -> value.value()),
                OptionalInt.empty());
    }

    private static DependencyKind declarationKind(
            DeclarationDependencySourceKind source, DeclarationDependencyEvidenceKind evidence) {
        if (evidence == DeclarationDependencyEvidenceKind.ANNOTATION) return DependencyKind.ANNOTATION;
        if (evidence == DeclarationDependencyEvidenceKind.GENERIC_ONLY) {
            return DependencyKind.GENERIC_SIGNATURE;
        }
        return switch (source) {
            case SUPERCLASS -> DependencyKind.EXTENDS;
            case INTERFACE -> DependencyKind.IMPLEMENTS;
            case FIELD_DESCRIPTOR, RECORD_COMPONENT_DESCRIPTOR -> DependencyKind.FIELD_TYPE;
            case METHOD_PARAMETER_DESCRIPTOR -> DependencyKind.METHOD_PARAMETER_TYPE;
            case METHOD_RETURN_DESCRIPTOR -> DependencyKind.METHOD_RETURN_TYPE;
            case CLASS_GENERIC_SIGNATURE, FIELD_GENERIC_SIGNATURE, METHOD_GENERIC_SIGNATURE,
                    RECORD_COMPONENT_GENERIC_SIGNATURE -> DependencyKind.GENERIC_SIGNATURE;
            case ANNOTATION_DEFAULT_VALUE, ANNOTATION_TYPE, ANNOTATION_VALUE ->
                    DependencyKind.ANNOTATION;
            case PERMITTED_SUBCLASS -> DependencyKind.TYPE_REFERENCE;
        };
    }

    private static DependencyKind accessKind(JavaCodeAccessKind kind) {
        return switch (kind) {
            case CONSTRUCTOR_CALL -> DependencyKind.CONSTRUCTOR_CALL;
            case FIELD_READ, FIELD_WRITE -> DependencyKind.FIELD_ACCESS;
            case METHOD_CALL -> DependencyKind.METHOD_CALL;
        };
    }

    private static Optional<String> targetType(JvmType type) {
        if (type instanceof JvmReferenceType reference) return Optional.of(reference.binaryName());
        if (type instanceof JvmArrayType array) return targetType(array.elementType());
        return Optional.empty();
    }
}
