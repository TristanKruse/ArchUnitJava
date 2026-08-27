package dev.archunitjava.diagram.plantuml;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.LayerModel;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.slices.SliceModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Byte-stable PlantUML export of current layer or slice dependencies. */
public final class PlantUmlExporter {
    private PlantUmlExporter() {}

    public static String layers(LayerModel layers, DependencyGraph graph) {
        Objects.requireNonNull(layers, "layers");
        return export(
                "layer",
                layers.layers().stream()
                        .map(layer -> new Group(layer.id().name(), layer.types()))
                        .toList(),
                graph);
    }

    public static byte[] layersBytes(LayerModel layers, DependencyGraph graph) {
        return layers(layers, graph).getBytes(StandardCharsets.UTF_8);
    }

    public static String slices(SliceModel slices, DependencyGraph graph) {
        Objects.requireNonNull(slices, "slices");
        return export(
                "slice",
                slices.slices().stream()
                        .map(slice -> new Group(slice.id().name(), slice.types()))
                        .toList(),
                graph);
    }

    public static byte[] slicesBytes(SliceModel slices, DependencyGraph graph) {
        return slices(slices, graph).getBytes(StandardCharsets.UTF_8);
    }

    private static String export(
            String stereotype, List<Group> groups, DependencyGraph source) {
        Objects.requireNonNull(source, "graph");
        List<Group> stableGroups = groups.stream()
                .sorted(java.util.Comparator.comparing(Group::name))
                .toList();
        if (stableGroups.isEmpty()) {
            throw new IllegalArgumentException("Cannot export an architecture without components");
        }
        TreeMap<String, String> aliasesByName = new TreeMap<>();
        for (int index = 0; index < stableGroups.size(); index++) {
            aliasesByName.put(stableGroups.get(index).name(), "c" + String.format("%04d", index + 1));
        }
        TreeMap<TypeId, String> aliasesByType = new TreeMap<>();
        stableGroups.forEach(group -> group.types().forEach(type ->
                aliasesByType.put(type, aliasesByName.get(group.name()))));
        TreeSet<Pair> edges = new TreeSet<>();
        DependencyGraph types = ProjectionPlan.types().withoutSelfEdges().apply(source).graph();
        for (DependencyEdge edge : types.edges()) {
            if (!(edge.origin() instanceof TypeId origin)
                    || !(edge.target() instanceof TypeId target)) continue;
            String originAlias = aliasesByType.get(origin);
            String targetAlias = aliasesByType.get(target);
            if (originAlias != null && targetAlias != null && !originAlias.equals(targetAlias)) {
                edges.add(new Pair(originAlias, targetAlias));
            }
        }
        StringBuilder result = new StringBuilder();
        result.append("@startuml\n");
        for (Group group : stableGroups) {
            result.append("component \"")
                    .append(escape(group.name()))
                    .append("\" as ")
                    .append(aliasesByName.get(group.name()))
                    .append(" <<")
                    .append(stereotype)
                    .append(">>\n");
        }
        edges.forEach(edge -> result.append(edge.origin())
                .append(" --> ")
                .append(edge.target())
                .append('\n'));
        return result.append("@enduml\n").toString();
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (Character.isISOControl(character)) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }

    private record Group(String name, List<TypeId> types) {
        private Group {
            Objects.requireNonNull(name, "name");
            types = types.stream().distinct().sorted().toList();
        }
    }

    private record Pair(String origin, String target) implements Comparable<Pair> {
        private String stableKey() {
            return origin + "->" + target;
        }

        @Override
        public int compareTo(Pair other) {
            return stableKey().compareTo(other.stableKey());
        }
    }
}
