package dev.archunitjava.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable hierarchy resolver that never loads classes and never guesses across gaps. */
public final class TypeHierarchy {
    private final Map<String, Node> nodes;

    private TypeHierarchy(Builder builder) {
        TreeMap<String, Node> values = new TreeMap<>();
        builder.external.values().forEach(stub -> values.put(stub.name().binaryName(), Node.external(stub)));
        builder.imported.values().forEach(type -> values.put(type.binaryName(), Node.imported(type)));
        TreeSet<String> referenced = new TreeSet<>();
        values.values().forEach(node -> {
            node.superclass.ifPresent(type -> referenced.add(type.binaryName()));
            node.interfaces.forEach(type -> referenced.add(type.binaryName()));
            node.permittedSubclasses.forEach(type -> referenced.add(type.binaryName()));
        });
        referenced.forEach(name -> values.putIfAbsent(name, Node.missing(name)));
        nodes = Map.copyOf(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TypeHierarchy of(Collection<JavaType> importedTypes) {
        return builder().addImported(importedTypes).build();
    }

    public List<HierarchyRelationship> directRelationships(String binaryName) {
        Node node = nodes.get(binaryName);
        if (node == null) return List.of();
        List<HierarchyRelationship> relationships = new ArrayList<>();
        node.superclass.ifPresent(parent -> relationships.add(new HierarchyRelationship(
                node.name, new JavaTypeName(parent.binaryName()), HierarchyRelationshipKind.EXTENDS_CLASS)));
        HierarchyRelationshipKind interfaceKind = node.kind
                .filter(kind -> kind == JavaTypeKind.INTERFACE || kind == JavaTypeKind.ANNOTATION)
                .map(ignored -> HierarchyRelationshipKind.EXTENDS_INTERFACE)
                .orElse(HierarchyRelationshipKind.IMPLEMENTS_INTERFACE);
        node.interfaces.forEach(parent -> relationships.add(new HierarchyRelationship(
                node.name, new JavaTypeName(parent.binaryName()), interfaceKind)));
        return relationships.stream().sorted().toList();
    }

    public HierarchyQueryResult transitiveSupertypes(String binaryName) {
        Node start = nodes.get(binaryName);
        if (start == null) {
            return new HierarchyQueryResult(
                    List.of(), List.of(new JavaTypeName(binaryName)), false, false);
        }
        TreeSet<JavaTypeName> result = new TreeSet<>();
        TreeSet<JavaTypeName> missing = new TreeSet<>();
        TreeSet<String> expanded = new TreeSet<>();
        ArrayDeque<PathStep> pending = new ArrayDeque<>();
        directNames(start).forEach(name -> pending.addLast(new PathStep(name, List.of(start.name.binaryName()))));
        boolean cycle = false;
        boolean complete = start.complete;
        while (!pending.isEmpty()) {
            PathStep step = pending.removeFirst();
            if (step.path.contains(step.name)) {
                cycle = true;
                complete = false;
                continue;
            }
            result.add(new JavaTypeName(step.name));
            Node node = nodes.get(step.name);
            if (node == null || !node.complete) {
                missing.add(new JavaTypeName(step.name));
                complete = false;
                continue;
            }
            if (!expanded.add(step.name)) continue;
            List<String> nextPath = new ArrayList<>(step.path);
            nextPath.add(step.name);
            directNames(node).forEach(name -> pending.addLast(new PathStep(name, List.copyOf(nextPath))));
        }
        result.remove(start.name);
        return new HierarchyQueryResult(
                List.copyOf(result), List.copyOf(missing), complete && !cycle, cycle);
    }

    public Assignability isAssignable(String targetBinaryName, String sourceBinaryName) {
        if (Objects.equals(targetBinaryName, sourceBinaryName)) return Assignability.YES;
        if (targetBinaryName.equals("java.lang.Object") && nodes.containsKey(sourceBinaryName)) {
            return Assignability.YES;
        }
        HierarchyQueryResult hierarchy = transitiveSupertypes(sourceBinaryName);
        boolean found = hierarchy.supertypes().stream()
                .anyMatch(type -> type.binaryName().equals(targetBinaryName));
        if (found) return Assignability.YES;
        return hierarchy.complete() ? Assignability.NO : Assignability.UNKNOWN;
    }

    /** Compares a sealed declaration with direct subtype evidence available in this model. */
    public SealedHierarchyResult sealedHierarchy(String binaryName) {
        JavaTypeName requested = new JavaTypeName(binaryName);
        Node subject = nodes.get(binaryName);
        if (subject == null || !subject.present) {
            return new SealedHierarchyResult(
                    requested,
                    false,
                    List.of(),
                    List.of(),
                    List.of(requested),
                    List.of(diagnostic(SealedHierarchyDiagnosticCode.QUERY_TYPE_MISSING, requested)),
                    false);
        }
        List<JavaTypeName> observed = nodes.values().stream()
                .filter(node -> node.present && directNames(node).contains(binaryName))
                .map(node -> node.name)
                .sorted()
                .toList();
        List<JavaTypeName> declared = subject.permittedSubclasses.stream()
                .map(type -> new JavaTypeName(type.binaryName()))
                .sorted()
                .toList();
        if (!subject.sealed) {
            return new SealedHierarchyResult(
                    requested, false, declared, observed, List.of(), List.of(), true);
        }

        List<SealedHierarchyDiagnostic> diagnostics = new ArrayList<>();
        TreeSet<JavaTypeName> missing = new TreeSet<>();
        if (declared.isEmpty()) {
            diagnostics.add(diagnostic(
                    SealedHierarchyDiagnosticCode.EMPTY_PERMITTED_SUBCLASS_LIST, null));
        }
        TreeSet<JavaTypeName> uniqueDeclared = new TreeSet<>();
        for (JavaTypeName permitted : declared) {
            if (!uniqueDeclared.add(permitted)) {
                diagnostics.add(diagnostic(
                        SealedHierarchyDiagnosticCode.DUPLICATE_PERMITTED_SUBCLASS, permitted));
                continue;
            }
            if (permitted.equals(requested)) {
                diagnostics.add(diagnostic(
                        SealedHierarchyDiagnosticCode.SELF_PERMITTED_SUBCLASS, permitted));
                continue;
            }
            Node candidate = nodes.get(permitted.binaryName());
            if (candidate == null || !candidate.present) {
                missing.add(permitted);
                diagnostics.add(diagnostic(
                        SealedHierarchyDiagnosticCode.MISSING_PERMITTED_SUBCLASS, permitted));
            } else if (directNames(candidate).contains(binaryName)) {
                // The declared and observed direct relationship agrees.
            } else if (!candidate.complete) {
                diagnostics.add(diagnostic(
                        SealedHierarchyDiagnosticCode.PERMITTED_RELATIONSHIP_UNKNOWN, permitted));
            } else {
                diagnostics.add(diagnostic(
                        SealedHierarchyDiagnosticCode.PERMITTED_SUBCLASS_NOT_DIRECT, permitted));
            }
        }
        for (JavaTypeName subtype : observed) {
            if (!uniqueDeclared.contains(subtype)) {
                diagnostics.add(diagnostic(
                        SealedHierarchyDiagnosticCode.OBSERVED_SUBCLASS_NOT_PERMITTED, subtype));
            }
        }
        return new SealedHierarchyResult(
                requested,
                true,
                declared,
                observed,
                List.copyOf(missing),
                diagnostics,
                diagnostics.isEmpty());
    }

    private static SealedHierarchyDiagnostic diagnostic(
            SealedHierarchyDiagnosticCode code, JavaTypeName relatedType) {
        return new SealedHierarchyDiagnostic(code, Optional.ofNullable(relatedType));
    }

    private static List<String> directNames(Node node) {
        TreeSet<String> names = new TreeSet<>();
        node.superclass.ifPresent(type -> names.add(type.binaryName()));
        node.interfaces.forEach(type -> names.add(type.binaryName()));
        return List.copyOf(names);
    }

    public static final class Builder {
        private final Map<String, JavaType> imported = new TreeMap<>();
        private final Map<String, ExternalTypeStub> external = new TreeMap<>();

        public Builder addImported(Collection<JavaType> types) {
            Objects.requireNonNull(types, "types");
            for (JavaType type : types) {
                JavaType value = Objects.requireNonNull(type, "type");
                JavaType previous = imported.put(value.binaryName(), value);
                if (previous != null) throw new IllegalArgumentException("Duplicate imported type: " + value.binaryName());
            }
            return this;
        }

        public Builder addExternal(ExternalTypeStub stub) {
            Objects.requireNonNull(stub, "stub");
            if (external.put(stub.name().binaryName(), stub) != null) {
                throw new IllegalArgumentException("Duplicate external stub: " + stub.name().binaryName());
            }
            return this;
        }

        public TypeHierarchy build() {
            return new TypeHierarchy(this);
        }
    }

    private record Node(
            JavaTypeName name,
            Optional<JavaTypeKind> kind,
            Optional<JvmReferenceType> superclass,
            List<JvmReferenceType> interfaces,
            boolean complete,
            boolean present,
            boolean sealed,
            List<JvmReferenceType> permittedSubclasses) {
        static Node imported(JavaType type) {
            return new Node(
                    type.name(),
                    Optional.of(type.kind()),
                    type.superclass(),
                    type.directInterfaces(),
                    true,
                    true,
                    type.isSealed(),
                    type.permittedSubclasses());
        }

        static Node external(ExternalTypeStub stub) {
            return new Node(
                    stub.name(),
                    stub.kind(),
                    stub.superclass(),
                    stub.directInterfaces(),
                    stub.hierarchyComplete(),
                    true,
                    false,
                    List.of());
        }

        static Node missing(String binaryName) {
            return new Node(
                    new JavaTypeName(binaryName),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    false,
                    false,
                    false,
                    List.of());
        }
    }

    private record PathStep(String name, List<String> path) {}
}
