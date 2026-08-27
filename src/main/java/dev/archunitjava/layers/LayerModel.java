package dev.archunitjava.layers;

import dev.archunitjava.graph.TypeId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable non-overlapping named layer memberships and retained unassigned imported types. */
public final class LayerModel {
    private final List<JavaLayer> layers;
    private final Map<TypeId, LayerId> memberships;
    private final List<TypeId> unassignedTypes;
    private final String definitionKey;

    LayerModel(
            Collection<JavaLayer> layers,
            Map<TypeId, LayerId> memberships,
            Collection<TypeId> unassignedTypes,
            String definitionKey) {
        this.layers = layers.stream()
                .map(value -> Objects.requireNonNull(value, "layer"))
                .distinct().sorted().toList();
        TreeMap<TypeId, LayerId> copy = new TreeMap<>();
        memberships.forEach((type, layer) -> copy.put(
                Objects.requireNonNull(type, "type"), Objects.requireNonNull(layer, "layer")));
        this.memberships = Map.copyOf(copy);
        this.unassignedTypes = unassignedTypes.stream()
                .map(value -> Objects.requireNonNull(value, "unassignedType"))
                .distinct().sorted().toList();
        if (definitionKey == null || definitionKey.isBlank()) {
            throw new IllegalArgumentException("definitionKey must not be blank");
        }
        this.definitionKey = definitionKey;
        validate();
    }

    public List<JavaLayer> layers() {
        return layers;
    }

    public Optional<LayerId> layerOf(TypeId type) {
        return Optional.ofNullable(memberships.get(Objects.requireNonNull(type, "type")));
    }

    public List<TypeId> unassignedTypes() {
        return unassignedTypes;
    }

    public String definitionKey() {
        return definitionKey;
    }

    private void validate() {
        TreeMap<TypeId, LayerId> fromLayers = new TreeMap<>();
        for (JavaLayer layer : layers) {
            for (TypeId type : layer.types()) {
                if (fromLayers.putIfAbsent(type, layer.id()) != null) {
                    throw new IllegalArgumentException("Overlapping layer memberships");
                }
            }
        }
        if (!fromLayers.equals(new TreeMap<>(memberships))) {
            throw new IllegalArgumentException("Membership index disagrees with layers");
        }
        if (unassignedTypes.stream().anyMatch(memberships::containsKey)) {
            throw new IllegalArgumentException("Assigned type also appears as unassigned");
        }
    }
}
