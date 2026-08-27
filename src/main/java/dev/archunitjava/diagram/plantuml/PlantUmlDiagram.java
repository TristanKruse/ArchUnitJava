package dev.archunitjava.diagram.plantuml;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable deterministic output of the bounded parser. */
public final class PlantUmlDiagram {
    private final List<PlantUmlComponent> components;
    private final List<PlantUmlEdge> edges;
    private final Map<String, PlantUmlComponent> byAlias;

    PlantUmlDiagram(
            Collection<PlantUmlComponent> components, Collection<PlantUmlEdge> edges) {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(edges, "edges");
        TreeMap<String, PlantUmlComponent> aliases = new TreeMap<>();
        HashSet<String> displayNames = new HashSet<>();
        for (PlantUmlComponent component : components) {
            PlantUmlComponent value = Objects.requireNonNull(component, "component");
            if (aliases.putIfAbsent(value.alias(), value) != null) {
                throw new InvalidPlantUmlException("Duplicate component alias: " + value.alias());
            }
            if (!displayNames.add(value.displayName())) {
                throw new InvalidPlantUmlException(
                        "Duplicate component display name: " + value.displayName());
            }
        }
        if (aliases.isEmpty()) {
            throw new InvalidPlantUmlException("At least one component is required");
        }
        TreeMap<String, PlantUmlEdge> pairs = new TreeMap<>();
        for (PlantUmlEdge edge : edges) {
            PlantUmlEdge value = Objects.requireNonNull(edge, "edge");
            if (!aliases.containsKey(value.originAlias()) || !aliases.containsKey(value.targetAlias())) {
                throw new InvalidPlantUmlException(
                        "Edge refers to an unknown component: " + value.pairKey());
            }
            if (pairs.putIfAbsent(value.pairKey(), value) != null) {
                throw new InvalidPlantUmlException("Duplicate component edge: " + value.pairKey());
            }
        }
        this.byAlias = Map.copyOf(aliases);
        this.components = List.copyOf(aliases.values());
        this.edges = pairs.values().stream().sorted().toList();
    }

    public List<PlantUmlComponent> components() {
        return components;
    }

    public List<PlantUmlEdge> edges() {
        return edges;
    }

    public PlantUmlComponent component(String alias) {
        PlantUmlComponent result = byAlias.get(Objects.requireNonNull(alias, "alias"));
        if (result == null) throw new IllegalArgumentException("Unknown component alias: " + alias);
        return result;
    }
}
