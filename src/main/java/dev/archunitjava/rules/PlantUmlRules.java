package dev.archunitjava.rules;

import dev.archunitjava.diagram.plantuml.PlantUmlAdherenceOptions;
import dev.archunitjava.diagram.plantuml.PlantUmlComponent;
import dev.archunitjava.diagram.plantuml.PlantUmlComponentId;
import dev.archunitjava.diagram.plantuml.PlantUmlDiagram;
import dev.archunitjava.diagram.plantuml.PlantUmlEdge;
import dev.archunitjava.diagram.plantuml.UnmappedDiagramDependencyPolicy;
import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.JavaLayer;
import dev.archunitjava.layers.LayerModel;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.SelectorDescription;
import dev.archunitjava.slices.JavaSlice;
import dev.archunitjava.slices.SliceModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Adheres selected layer or slice components to a safely parsed PlantUML graph. */
public final class PlantUmlRules {
    private PlantUmlRules() {}

    public static ArchitectureRule layers(
            TypeModelResult model,
            LayerModel layers,
            DependencyGraph graph,
            PlantUmlDiagram diagram) {
        return layers(model, layers, graph, diagram, PlantUmlAdherenceOptions.strict());
    }

    public static ArchitectureRule layers(
            TypeModelResult model,
            LayerModel layers,
            DependencyGraph graph,
            PlantUmlDiagram diagram,
            PlantUmlAdherenceOptions options) {
        Objects.requireNonNull(layers, "layers");
        List<Group> groups = layers.layers().stream()
                .map(layer -> new Group(layer.id().name(), layer.id(), layer.types()))
                .toList();
        return rule(model, graph, diagram, options, "layer", layers.definitionKey(), groups);
    }

    public static ArchitectureRule slices(
            TypeModelResult model,
            SliceModel slices,
            DependencyGraph graph,
            PlantUmlDiagram diagram) {
        return slices(model, slices, graph, diagram, PlantUmlAdherenceOptions.strict());
    }

    public static ArchitectureRule slices(
            TypeModelResult model,
            SliceModel slices,
            DependencyGraph graph,
            PlantUmlDiagram diagram,
            PlantUmlAdherenceOptions options) {
        Objects.requireNonNull(slices, "slices");
        List<Group> groups = slices.slices().stream()
                .map(slice -> new Group(slice.id().name(), slice.id(), slice.types()))
                .toList();
        return rule(model, graph, diagram, options, "slice", slices.definitionKey(), groups);
    }

    private static ArchitectureRule rule(
            TypeModelResult model,
            DependencyGraph graph,
            PlantUmlDiagram diagram,
            PlantUmlAdherenceOptions options,
            String groupKind,
            String definitionKey,
            List<Group> groups) {
        TypeModelResult typeModel = Objects.requireNonNull(model, "model");
        DependencyGraph source = Objects.requireNonNull(graph, "graph");
        PlantUmlDiagram architecture = Objects.requireNonNull(diagram, "diagram");
        PlantUmlAdherenceOptions strictness = Objects.requireNonNull(options, "options");
        DiagramDomain domain = domain(typeModel, architecture, groupKind, groups);
        Observation observation = observe(source, domain);
        SelectorDescription selection = new SelectorDescription(
                "PlantUML " + groupKind + " components " + architecture.components().stream()
                        .map(PlantUmlComponent::displayName).sorted().toList());
        String identity = RuleIdentities.semantic(
                "plantuml-adherence",
                groupKind,
                definitionKey,
                diagramKey(architecture),
                Boolean.toString(strictness.requireDeclaredEdgesObserved()),
                strictness.unmappedDependencyPolicy().name());
        return ArchitectureRules.define(
                identity,
                selection + " adhere to observed Java dependencies",
                (metadata, checkOptions) -> RuleTerminal.evaluate(
                        metadata,
                        checkOptions,
                        selection,
                        architecture.components().size(),
                        diagnostics -> evaluate(
                                metadata,
                                architecture,
                                strictness,
                                domain,
                                observation,
                                diagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            PlantUmlDiagram diagram,
            PlantUmlAdherenceOptions options,
            DiagramDomain domain,
            Observation observation,
            List<Diagnostic> diagnostics) {
        Map<Pair, PlantUmlEdge> declared = new TreeMap<>();
        diagram.edges().forEach(edge -> declared.put(
                new Pair(edge.originAlias(), edge.targetAlias()), edge));
        List<Violation> violations = new ArrayList<>();
        observation.mappedEdges().forEach((pair, edge) -> {
            if (!declared.containsKey(pair)) {
                violations.add(forbidden(metadata, pair, edge));
            }
        });
        if (options.unmappedDependencyPolicy() == UnmappedDiagramDependencyPolicy.FORBID) {
            observation.unmappedEdges().forEach(edge ->
                    violations.add(forbiddenUnmapped(metadata, edge)));
        }
        if (options.requireDeclaredEdgesObserved()) {
            declared.forEach((pair, edge) -> {
                if (!observation.mappedEdges().containsKey(pair)) {
                    violations.add(missing(metadata, pair, edge, domain));
                }
            });
        }
        List<Violation> stable = violations.stream().sorted().toList();
        return stable.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, stable, diagnostics);
    }

    private static Violation forbidden(
            RuleMetadata metadata, Pair pair, ObservedEdge edge) {
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), "forbidden", pair.stableKey())),
                "plantuml.edge.forbidden",
                metadata.severity(),
                componentSubjects(pair),
                edge.evidence(),
                Map.of(
                        "declared", "false",
                        "originAlias", pair.origin(),
                        "targetAlias", pair.target(),
                        "underlyingTypeEdges", edge.typeEdges().toString()));
    }

    private static Violation forbiddenUnmapped(
            RuleMetadata metadata, UnmappedEdge edge) {
        List<ViolationSubject> subjects = new ArrayList<>();
        edge.originAlias().ifPresent(alias -> subjects.add(
                new ViolationSubject("originComponent", new PlantUmlComponentId(alias))));
        if (edge.originAlias().isEmpty()) {
            subjects.add(new ViolationSubject("originType", edge.originType()));
        }
        edge.targetAlias().ifPresent(alias -> subjects.add(
                new ViolationSubject("targetComponent", new PlantUmlComponentId(alias))));
        if (edge.targetAlias().isEmpty()) {
            subjects.add(new ViolationSubject("targetType", edge.targetType()));
        }
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(),
                        "forbidden-unmapped",
                        edge.originType().stableKey(),
                        edge.targetType().stableKey(),
                        edge.kind())),
                "plantuml.edge.forbidden",
                metadata.severity(),
                subjects,
                edge.evidence(),
                Map.of(
                        "declared", "false",
                        "originAlias", edge.originAlias().orElse("<unmapped>"),
                        "targetAlias", edge.targetAlias().orElse("<unmapped>"),
                        "underlyingTypeEdges",
                        List.of(edge.originType().stableKey() + "->" + edge.targetType().stableKey()
                                        + ":" + edge.kind())
                                .toString()));
    }

    private static Violation missing(
            RuleMetadata metadata,
            Pair pair,
            PlantUmlEdge edge,
            DiagramDomain domain) {
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), "missing", pair.stableKey(), edge.arrow().name())),
                "plantuml.edge.missing",
                metadata.severity(),
                componentSubjects(pair),
                domain.declarationEvidence(pair),
                Map.of(
                        "arrow", edge.arrow().token(),
                        "declared", "true",
                        "originAlias", pair.origin(),
                        "targetAlias", pair.target()));
    }

    private static List<ViolationSubject> componentSubjects(Pair pair) {
        return List.of(
                new ViolationSubject("originComponent", new PlantUmlComponentId(pair.origin())),
                new ViolationSubject("targetComponent", new PlantUmlComponentId(pair.target())));
    }

    private static DiagramDomain domain(
            TypeModelResult model,
            PlantUmlDiagram diagram,
            String expectedStereotype,
            List<Group> groups) {
        Map<String, Group> byName = new TreeMap<>();
        groups.forEach(group -> byName.put(group.name(), group));
        Map<String, Group> byAlias = new TreeMap<>();
        for (PlantUmlComponent component : diagram.components()) {
            component.stereotype().ifPresent(value -> {
                if (!value.equals(expectedStereotype)) {
                    throw new IllegalArgumentException(
                            "Component " + component.alias() + " has stereotype " + value
                                    + ", expected " + expectedStereotype);
                }
            });
            Group group = byName.get(component.displayName());
            if (group == null) {
                throw new IllegalArgumentException(
                        "PlantUML component does not match a " + expectedStereotype
                                + ": " + component.displayName());
            }
            byAlias.put(component.alias(), group);
        }
        Map<TypeId, String> aliasesByType = new TreeMap<>();
        byAlias.forEach((alias, group) -> group.types().forEach(type -> aliasesByType.put(type, alias)));
        Map<TypeId, DependencyEvidence> declarations = new TreeMap<>();
        for (JavaType type : model.types()) {
            declarations.put(
                    TypeId.ofBinaryName(type.binaryName()),
                    DependencyEvidence.at(type.location().resource().locationId()));
        }
        return new DiagramDomain(Map.copyOf(byAlias), Map.copyOf(aliasesByType), Map.copyOf(declarations));
    }

    private static Observation observe(DependencyGraph graph, DiagramDomain domain) {
        DependencyGraph types = ProjectionPlan.types().withoutSelfEdges().apply(graph).graph();
        TreeMap<Pair, MutableObservedEdge> mapped = new TreeMap<>();
        List<UnmappedEdge> unmapped = new ArrayList<>();
        for (DependencyEdge edge : types.edges()) {
            if (!(edge.origin() instanceof TypeId origin)
                    || !(edge.target() instanceof TypeId target)) continue;
            String originAlias = domain.aliasesByType().get(origin);
            String targetAlias = domain.aliasesByType().get(target);
            if (originAlias == null && targetAlias == null) continue;
            if (originAlias == null || targetAlias == null) {
                unmapped.add(new UnmappedEdge(
                        origin,
                        target,
                        Optional.ofNullable(originAlias),
                        Optional.ofNullable(targetAlias),
                        edge.kind().name(),
                        edge.evidence()));
                continue;
            }
            if (originAlias.equals(targetAlias)) continue;
            mapped.computeIfAbsent(
                            new Pair(originAlias, targetAlias), ignored -> new MutableObservedEdge())
                    .add(origin, target, edge);
        }
        TreeMap<Pair, ObservedEdge> frozen = new TreeMap<>();
        mapped.forEach((pair, value) -> frozen.put(pair, value.freeze()));
        return new Observation(Map.copyOf(frozen), unmapped.stream().sorted().toList());
    }

    private static String diagramKey(PlantUmlDiagram diagram) {
        return "components=" + diagram.components() + ";edges=" + diagram.edges();
    }

    private record Group(String name, StableId id, List<TypeId> types) {
        private Group {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(id, "id");
            types = types.stream().distinct().sorted().toList();
        }
    }

    private record Pair(String origin, String target) implements Comparable<Pair> {
        private Pair {
            new PlantUmlComponentId(origin);
            new PlantUmlComponentId(target);
        }

        private String stableKey() {
            return origin + "->" + target;
        }

        @Override
        public int compareTo(Pair other) {
            return stableKey().compareTo(other.stableKey());
        }
    }

    private record TypeEdge(TypeId origin, TypeId target, String kind)
            implements Comparable<TypeEdge> {
        private String stableKey() {
            return origin.stableKey() + "->" + target.stableKey() + ":" + kind;
        }

        @Override
        public int compareTo(TypeEdge other) {
            return stableKey().compareTo(other.stableKey());
        }
    }

    private record ObservedEdge(List<TypeEdge> typeEdges, List<DependencyEvidence> evidence) {
        private ObservedEdge {
            typeEdges = typeEdges.stream().distinct().sorted().toList();
            evidence = evidence.stream().distinct().sorted().toList();
        }
    }

    private static final class MutableObservedEdge {
        private final TreeSet<TypeEdge> typeEdges = new TreeSet<>();
        private final TreeSet<DependencyEvidence> evidence = new TreeSet<>();

        private void add(TypeId origin, TypeId target, DependencyEdge edge) {
            typeEdges.add(new TypeEdge(origin, target, edge.kind().name()));
            evidence.addAll(edge.evidence());
        }

        private ObservedEdge freeze() {
            return new ObservedEdge(List.copyOf(typeEdges), List.copyOf(evidence));
        }
    }

    private record UnmappedEdge(
            TypeId originType,
            TypeId targetType,
            Optional<String> originAlias,
            Optional<String> targetAlias,
            String kind,
            List<DependencyEvidence> evidence)
            implements Comparable<UnmappedEdge> {
        private UnmappedEdge {
            Objects.requireNonNull(originType, "originType");
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(originAlias, "originAlias");
            Objects.requireNonNull(targetAlias, "targetAlias");
            Objects.requireNonNull(kind, "kind");
            evidence = evidence.stream().distinct().sorted().toList();
        }

        private String stableKey() {
            return originType.stableKey() + "->" + targetType.stableKey() + ":" + kind;
        }

        @Override
        public int compareTo(UnmappedEdge other) {
            return stableKey().compareTo(other.stableKey());
        }
    }

    private record Observation(
            Map<Pair, ObservedEdge> mappedEdges, List<UnmappedEdge> unmappedEdges) {}

    private record DiagramDomain(
            Map<String, Group> groupsByAlias,
            Map<TypeId, String> aliasesByType,
            Map<TypeId, DependencyEvidence> declarations) {
        private List<DependencyEvidence> declarationEvidence(Pair pair) {
            return java.util.stream.Stream.of(pair.origin(), pair.target())
                    .map(groupsByAlias::get)
                    .map(Group::types)
                    .flatMap(Collection::stream)
                    .map(declarations::get)
                    .filter(Objects::nonNull)
                    .distinct().sorted().toList();
        }
    }
}
