package dev.archunitjava.selector;

import dev.archunitjava.model.AnnotationSiteKind;
import dev.archunitjava.model.Assignability;
import dev.archunitjava.model.JavaAnnotationOccurrence;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeName;
import dev.archunitjava.model.MetaAnnotationResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SemanticMatchers {
    private SemanticMatchers() {}

    static boolean typeAnnotation(
            JavaType type, AnnotationQuery query, TypeSelectionContext context) {
        return switch (query.mode()) {
            case DIRECT_DECLARATION -> exact(
                    type.annotations(), query, Set.of(AnnotationSiteKind.TYPE_DECLARATION));
            case TYPE_USE -> exact(type.annotations(), query, Set.of(AnnotationSiteKind.TYPE_USE));
            case META_ANNOTATION -> meta(
                    type.binaryName(), type.annotations(), query,
                    Set.of(AnnotationSiteKind.TYPE_DECLARATION), context);
            case INHERITED_DECLARATION -> inherited(type, query, context);
        };
    }

    static boolean memberAnnotation(
            JavaMember member, AnnotationQuery query, TypeSelectionContext context) {
        if (query.mode() == AnnotationMatchMode.INHERITED_DECLARATION) {
            throw new IllegalArgumentException("Members do not inherit declaration annotations");
        }
        Set<AnnotationSiteKind> declarations = Set.of(
                AnnotationSiteKind.FIELD_DECLARATION,
                AnnotationSiteKind.METHOD_DECLARATION,
                AnnotationSiteKind.CONSTRUCTOR_DECLARATION);
        return switch (query.mode()) {
            case DIRECT_DECLARATION -> exact(member.annotations(), query, declarations);
            case TYPE_USE -> exact(member.annotations(), query, Set.of(AnnotationSiteKind.TYPE_USE));
            case META_ANNOTATION -> meta(
                    member.signature().stableKey(), member.annotations(), query, declarations, context);
            case INHERITED_DECLARATION -> throw new AssertionError();
        };
    }

    static boolean assignability(
            JavaType type,
            JavaTypeName other,
            boolean assignableTo,
            UnknownHierarchyPolicy policy,
            TypeSelectionContext context) {
        Assignability result = assignableTo
                ? context.hierarchy().isAssignable(other.binaryName(), type.binaryName())
                : context.hierarchy().isAssignable(type.binaryName(), other.binaryName());
        if (result == Assignability.YES) return true;
        if (result == Assignability.NO) return false;
        String relationship = assignableTo ? "assignable to " : "assignable from ";
        return context.unknown(
                SelectionDiagnosticCode.UNKNOWN_ASSIGNABILITY,
                type.binaryName(),
                relationship + other.binaryName() + " is unknown because hierarchy evidence is incomplete",
                policy);
    }

    private static boolean inherited(
            JavaType subject, AnnotationQuery query, TypeSelectionContext context) {
        Set<JavaTypeName> visited = new HashSet<>();
        JavaType current = subject;
        while (current.superclass().isPresent()) {
            JavaTypeName parentName = new JavaTypeName(current.superclass().orElseThrow().binaryName());
            if (!visited.add(parentName)) {
                return context.unknown(
                        SelectionDiagnosticCode.INCOMPLETE_INHERITED_ANNOTATION,
                        subject.binaryName(),
                        "cyclic superclass path at " + parentName.binaryName(),
                        query.unknownPolicy());
            }
            JavaType parent = context.type(parentName).orElse(null);
            if (parent == null) {
                return context.unknown(
                        SelectionDiagnosticCode.INCOMPLETE_INHERITED_ANNOTATION,
                        subject.binaryName(),
                        "missing superclass " + parentName.binaryName(),
                        query.unknownPolicy());
            }
            if (exact(parent.annotations(), query, Set.of(AnnotationSiteKind.TYPE_DECLARATION))) {
                return true;
            }
            current = parent;
        }
        return false;
    }

    private static boolean meta(
            String subject,
            List<JavaAnnotationOccurrence> occurrences,
            AnnotationQuery query,
            Set<AnnotationSiteKind> sites,
            TypeSelectionContext context) {
        boolean incomplete = false;
        for (JavaAnnotationOccurrence occurrence : occurrences) {
            if (!sites.contains(occurrence.site().kind()) || !visible(occurrence, query)) continue;
            MetaAnnotationResult result = context.metaAnnotations().resolve(
                    occurrence.annotation().type().binaryName(), query.maximumMetaDepth());
            if (result.annotations().stream().anyMatch(value ->
                    value.binaryName().equals(query.annotationType().binaryName()))) return true;
            incomplete |= !result.complete();
        }
        if (!incomplete) return false;
        return context.unknown(
                SelectionDiagnosticCode.INCOMPLETE_META_ANNOTATION,
                subject,
                "meta-annotation traversal for " + query.annotationType().binaryName()
                        + " was incomplete",
                query.unknownPolicy());
    }

    private static boolean exact(
            List<JavaAnnotationOccurrence> occurrences,
            AnnotationQuery query,
            Set<AnnotationSiteKind> sites) {
        return occurrences.stream()
                .filter(value -> sites.contains(value.site().kind()))
                .filter(value -> visible(value, query))
                .anyMatch(value -> value.annotation().type().binaryName()
                        .equals(query.annotationType().binaryName()));
    }

    private static boolean visible(JavaAnnotationOccurrence value, AnnotationQuery query) {
        return query.visibility().isEmpty()
                || query.visibility().orElseThrow() == value.visibility();
    }
}
