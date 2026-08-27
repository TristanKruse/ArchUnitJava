package dev.archunitjava.presets;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.LayerDefinitions;
import dev.archunitjava.layers.LayerModel;
import dev.archunitjava.layers.LayerOverlapPolicy;
import dev.archunitjava.layers.LayerSelector;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.rules.ArchitectureRule;
import dev.archunitjava.rules.CoverageRules;
import dev.archunitjava.rules.LayerRules;
import dev.archunitjava.rules.PublicInterfaceRules;
import dev.archunitjava.rules.TypeCoveragePolicy;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.MemberVisibility;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, inspectable preset that expands into ordinary architecture rules. */
public final class ArchitecturePreset {
    private final String name;
    private final TypeModelResult model;
    private final DependencyGraph graph;
    private final List<PresetLayer> layers;
    private final List<PresetRule> ruleDefinitions;
    private final TypeSelector exclusions;

    private ArchitecturePreset(
            String name,
            TypeModelResult model,
            DependencyGraph graph,
            Collection<PresetLayer> layers,
            Collection<PresetRule> rules,
            TypeSelector exclusions) {
        this.name = text(name, "preset name");
        this.model = Objects.requireNonNull(model, "model");
        this.graph = Objects.requireNonNull(graph, "graph");
        this.layers = unique(layers, PresetLayer::name, "layer");
        this.ruleDefinitions = unique(rules, PresetRule::key, "rule");
        this.exclusions = Objects.requireNonNull(exclusions, "exclusions");
        if (this.layers.isEmpty()) throw new IllegalArgumentException("A preset requires at least one layer");
        if (this.ruleDefinitions.isEmpty()) throw new IllegalArgumentException("A preset requires at least one rule");
        validateReferences(this.layers, this.ruleDefinitions);
    }

    public static ArchitecturePreset create(
            String name,
            TypeModelResult model,
            DependencyGraph graph,
            Collection<PresetLayer> layers,
            Collection<PresetRule> rules) {
        return new ArchitecturePreset(
                name, model, graph, layers, rules, TypeSelector.none());
    }

    public String name() {
        return name;
    }

    public List<PresetLayer> layers() {
        return layers;
    }

    public List<PresetRule> ruleDefinitions() {
        return ruleDefinitions;
    }

    public TypeSelector exclusions() {
        return exclusions;
    }

    public LayerModel layerModel() {
        LayerDefinitions.Builder definitions = LayerDefinitions.builder()
                .overlapPolicy(LayerOverlapPolicy.FIRST_BY_NAME);
        layers.forEach(layer -> definitions.optional(
                layer.name(), layer.selector().excluding(exclusions)));
        return definitions.build(model);
    }

    /** Expands every recipe to a normal independently decoratable architecture rule. */
    public List<ArchitectureRule> rules() {
        LayerModel layerModel = layerModel();
        DependencyGraph includedGraph = withoutExcludedTypes();
        List<ArchitectureRule> expanded = new ArrayList<>();
        for (PresetRule definition : ruleDefinitions) {
            ArchitectureRule rule = expand(definition, layerModel, includedGraph);
            if (definition.displayName().isPresent()) {
                rule = rule.as(definition.displayName().orElseThrow());
            }
            if (definition.rationale().isPresent()) {
                rule = rule.because(definition.rationale().orElseThrow());
            }
            expanded.add(rule.tagged("preset:" + name, "preset-rule:" + definition.key()));
        }
        return List.copyOf(expanded);
    }

    public List<RuleResult> check() {
        return check(CheckOptions.defaults());
    }

    public List<RuleResult> check(CheckOptions options) {
        Objects.requireNonNull(options, "options");
        return rules().stream().map(rule -> rule.check(options)).toList();
    }

    public ArchitecturePreset as(String newName) {
        return copy(newName, layers, ruleDefinitions, exclusions);
    }

    public ArchitecturePreset excludingTypes(TypeSelector selector) {
        TypeSelector value = Objects.requireNonNull(selector, "selector");
        return copy(name, layers, ruleDefinitions, exclusions.or(value));
    }

    public ArchitecturePreset renamedLayer(String oldName, String newName) {
        String oldValue = text(oldName, "old layer name");
        String newValue = text(newName, "new layer name");
        boolean found = layers.stream().anyMatch(layer -> layer.name().equals(oldValue));
        if (!found) throw new IllegalArgumentException("Unknown preset layer: " + oldValue);
        List<PresetLayer> renamedLayers = layers.stream()
                .map(layer -> layer.name().equals(oldValue)
                        ? new PresetLayer(newValue, layer.selector()) : layer)
                .toList();
        List<PresetRule> renamedRules = ruleDefinitions.stream()
                .map(rule -> rule.renamedLayer(oldValue, newValue))
                .toList();
        return copy(name, renamedLayers, renamedRules, exclusions);
    }

    public ArchitecturePreset withRuleDisplayName(String key, String displayName) {
        return replaceRule(key, rule -> rule.as(displayName));
    }

    public ArchitecturePreset becauseRule(String key, String rationale) {
        return replaceRule(key, rule -> rule.because(rationale));
    }

    public ArchitecturePreset withoutRule(String key) {
        String value = text(key, "rule key");
        List<PresetRule> retained = ruleDefinitions.stream()
                .filter(rule -> !rule.key().equals(value))
                .toList();
        if (retained.size() == ruleDefinitions.size()) {
            throw new IllegalArgumentException("Unknown preset rule: " + value);
        }
        return copy(name, layers, retained, exclusions);
    }

    public ArchitecturePreset withRule(PresetRule rule) {
        List<PresetRule> extended = new ArrayList<>(ruleDefinitions);
        extended.add(Objects.requireNonNull(rule, "rule"));
        return copy(name, layers, extended, exclusions);
    }

    public ArchitecturePreset withLayer(PresetLayer layer) {
        List<PresetLayer> extended = new ArrayList<>(layers);
        extended.add(Objects.requireNonNull(layer, "layer"));
        return copy(name, extended, ruleDefinitions, exclusions);
    }

    public ArchitecturePreset withoutLayer(String layerName) {
        String value = text(layerName, "layer name");
        List<PresetLayer> retainedLayers = layers.stream()
                .filter(layer -> !layer.name().equals(value))
                .toList();
        if (retainedLayers.size() == layers.size()) {
            throw new IllegalArgumentException("Unknown preset layer: " + value);
        }
        List<PresetRule> retainedRules = ruleDefinitions.stream()
                .filter(rule -> !rule.subjects().contains(value)
                        && !rule.relatedLayers().contains(value))
                .toList();
        return copy(name, retainedLayers, retainedRules, exclusions);
    }

    private ArchitectureRule expand(
            PresetRule definition, LayerModel layerModel, DependencyGraph includedGraph) {
        return switch (definition.mode()) {
            case EXACTLY_ONE_LAYER -> CoverageRules.types(
                    model,
                    TypeSelector.all(),
                    exclusions,
                    layers.stream()
                            .map(layer -> TypeCoveragePolicy.named(
                                    layer.name(), layer.selector().excluding(exclusions)))
                            .toList());
            case MAY_ONLY_ACCESS -> LayerRules.mayOnlyAccess(
                    layerModel,
                    includedGraph,
                    one(definition.subjects()),
                    possiblyEmpty(definition.relatedLayers()));
            case NO_ACCESS -> LayerRules.noAccess(
                    layerModel,
                    includedGraph,
                    one(definition.subjects()),
                    possiblyEmpty(definition.relatedLayers()));
            case ONLY_ACCESSED_BY -> LayerRules.onlyAccessedBy(
                    layerModel,
                    includedGraph,
                    one(definition.subjects()),
                    possiblyEmpty(definition.relatedLayers()));
            case ISOLATED -> LayerRules.isolated(
                    layerModel, includedGraph, one(definition.subjects()));
            case PUBLIC_INTERFACE -> PublicInterfaceRules.onlyAccessApprovedInterfaces(
                    model,
                    MemberSelector.declaredBy(selectors(definition.subjects()))
                            .and(MemberSelector.codeUnits()),
                    selectors(definition.relatedLayers()),
                    MemberSelector.declaredBy(selectors(definition.relatedLayers()))
                            .and(MemberSelector.visibility(MemberVisibility.PUBLIC)));
        };
    }

    private TypeSelector selectors(Collection<String> layerNames) {
        List<TypeSelector> values = layerNames.stream()
                .map(this::layer)
                .map(PresetLayer::selector)
                .map(selector -> selector.excluding(exclusions))
                .toList();
        return TypeSelector.anyOf(values);
    }

    private PresetLayer layer(String layerName) {
        return layers.stream()
                .filter(layer -> layer.name().equals(layerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown preset layer: " + layerName));
    }

    private DependencyGraph withoutExcludedTypes() {
        Set<TypeId> excluded = exclusions.selectFrom(model).selected().stream()
                .map(type -> TypeId.ofBinaryName(type.binaryName()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        DependencyGraph.Builder result = DependencyGraph.builder();
        Set<StableId> includedNodes = graph.nodes().stream()
                .map(node -> node.id())
                .filter(id -> !excluded.contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        includedNodes.forEach(result::addNode);
        for (DependencyEdge edge : graph.edges()) {
            if (!includedNodes.contains(edge.origin()) || !includedNodes.contains(edge.target())) continue;
            edge.evidence().forEach(evidence -> result.addDependency(
                    edge.origin(), edge.target(), edge.kind(), evidence));
        }
        return result.build();
    }

    private ArchitecturePreset replaceRule(
            String key, java.util.function.UnaryOperator<PresetRule> replacement) {
        String value = text(key, "rule key");
        boolean[] found = {false};
        List<PresetRule> replaced = ruleDefinitions.stream()
                .map(rule -> {
                    if (!rule.key().equals(value)) return rule;
                    found[0] = true;
                    return replacement.apply(rule);
                })
                .toList();
        if (!found[0]) throw new IllegalArgumentException("Unknown preset rule: " + value);
        return copy(name, layers, replaced, exclusions);
    }

    private ArchitecturePreset copy(
            String newName,
            Collection<PresetLayer> newLayers,
            Collection<PresetRule> newRules,
            TypeSelector newExclusions) {
        return new ArchitecturePreset(
                newName, model, graph, newLayers, newRules, newExclusions);
    }

    private static LayerSelector one(List<String> names) {
        return LayerSelector.named(names.getFirst());
    }

    private static LayerSelector possiblyEmpty(List<String> names) {
        return names.isEmpty() ? LayerSelector.none() : LayerSelector.names(names);
    }

    private static void validateReferences(
            List<PresetLayer> layers, List<PresetRule> rules) {
        Set<String> names = layers.stream().map(PresetLayer::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (PresetRule rule : rules) {
            java.util.stream.Stream.concat(
                            rule.subjects().stream(), rule.relatedLayers().stream())
                    .filter(value -> !names.contains(value))
                    .findFirst()
                    .ifPresent(value -> {
                        throw new IllegalArgumentException(
                                "Rule " + rule.key() + " refers to unknown layer " + value);
                    });
        }
    }

    private static <T> List<T> unique(
            Collection<T> values,
            java.util.function.Function<T, String> key,
            String role) {
        Objects.requireNonNull(values, role + "s");
        List<T> stable = values.stream()
                .map(value -> Objects.requireNonNull(value, role))
                .sorted(java.util.Comparator.comparing(key))
                .toList();
        Set<String> seen = new HashSet<>();
        for (T value : stable) {
            if (!seen.add(key.apply(value))) {
                throw new IllegalArgumentException("Duplicate preset " + role + ": " + key.apply(value));
            }
        }
        return stable;
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value.trim();
    }
}
