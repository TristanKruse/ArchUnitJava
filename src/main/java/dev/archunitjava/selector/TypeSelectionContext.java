package dev.archunitjava.selector;

import dev.archunitjava.model.JavaNestingKind;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeName;
import dev.archunitjava.model.MetaAnnotationResolver;
import dev.archunitjava.model.TypeHierarchy;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class TypeSelectionContext {
    private final Map<JavaTypeName, JavaType> types;
    private final TypeHierarchy hierarchy;
    private final MetaAnnotationResolver metaAnnotations;
    private final Map<JavaTypeName, Optional<String>> canonicalNames = new HashMap<>();
    private final Set<SelectionDiagnostic> diagnostics = new TreeSet<>();

    TypeSelectionContext(Collection<JavaType> universe) {
        TreeMap<JavaTypeName, JavaType> indexed = new TreeMap<>();
        for (JavaType type : universe) {
            JavaType current = indexed.get(type.name());
            if (current == null || type.compareTo(current) < 0) indexed.put(type.name(), type);
        }
        types = Map.copyOf(indexed);
        hierarchy = TypeHierarchy.of(indexed.values());
        metaAnnotations = new MetaAnnotationResolver(indexed.values());
    }

    Optional<String> canonicalName(JavaType type) {
        return canonicalName(type, new HashSet<>());
    }

    Optional<String> simpleName(JavaType type) {
        return switch (type.nesting().kind()) {
            case TOP_LEVEL -> Optional.of(type.name().simpleName());
            case ANONYMOUS -> Optional.empty();
            case LOCAL, MEMBER, UNKNOWN -> type.nesting().simpleName();
        };
    }

    Set<SelectionDiagnostic> diagnostics() {
        return Set.copyOf(diagnostics);
    }

    Optional<JavaType> type(JavaTypeName name) {
        return Optional.ofNullable(types.get(name));
    }

    TypeHierarchy hierarchy() {
        return hierarchy;
    }

    MetaAnnotationResolver metaAnnotations() {
        return metaAnnotations;
    }

    boolean unknown(
            SelectionDiagnosticCode code,
            String subject,
            String detail,
            UnknownHierarchyPolicy policy) {
        SelectionDiagnostic diagnostic = new SelectionDiagnostic(code, subject, detail);
        diagnostics.add(diagnostic);
        return switch (policy) {
            case INCLUDE -> true;
            case EXCLUDE -> false;
            case FAIL -> throw new IncompleteSelectionException(diagnostic);
        };
    }

    private Optional<String> canonicalName(JavaType type, Set<JavaTypeName> visiting) {
        Optional<String> cached = canonicalNames.get(type.name());
        if (cached != null) return cached;
        if (!visiting.add(type.name())) {
            diagnostics.add(new SelectionDiagnostic(
                    SelectionDiagnosticCode.CYCLIC_LEXICAL_OWNERSHIP,
                    type.binaryName(),
                    "lexical ownership returns to " + type.binaryName()));
            return Optional.empty();
        }
        Optional<String> result = switch (type.nesting().kind()) {
            case TOP_LEVEL -> Optional.of(type.binaryName());
            case ANONYMOUS, LOCAL -> Optional.empty();
            case UNKNOWN -> {
                diagnostics.add(new SelectionDiagnostic(
                        SelectionDiagnosticCode.UNKNOWN_NESTING_EVIDENCE,
                        type.binaryName(),
                        "canonical name requires resolved nesting metadata"));
                yield Optional.empty();
            }
            case MEMBER -> memberCanonicalName(type, visiting);
        };
        visiting.remove(type.name());
        canonicalNames.put(type.name(), result);
        return result;
    }

    private Optional<String> memberCanonicalName(JavaType type, Set<JavaTypeName> visiting) {
        JavaTypeName ownerName = type.nesting().lexicalOwner().orElse(null);
        JavaType owner = ownerName == null ? null : types.get(ownerName);
        if (owner == null) {
            diagnostics.add(new SelectionDiagnostic(
                    SelectionDiagnosticCode.MISSING_LEXICAL_OWNER,
                    type.binaryName(),
                    "missing imported owner " + (ownerName == null ? "<unknown>" : ownerName.binaryName())));
            return Optional.empty();
        }
        Optional<String> ownerCanonical = canonicalName(owner, visiting);
        Optional<String> simple = type.nesting().simpleName();
        if (ownerCanonical.isEmpty() || simple.isEmpty()) return Optional.empty();
        return Optional.of(ownerCanonical.orElseThrow() + "." + simple.orElseThrow());
    }
}
