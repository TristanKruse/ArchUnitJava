package dev.archunitjava.layers;

import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Reusable required and optional named layer definitions. */
public final class LayerDefinitions {
    private final List<Definition> definitions;
    private final LayerOverlapPolicy overlapPolicy;

    private LayerDefinitions(Builder builder) {
        TreeMap<LayerId, Definition> unique = new TreeMap<>();
        for (Definition definition : builder.definitions) {
            if (unique.putIfAbsent(definition.id, definition) != null) {
                throw new IllegalArgumentException("Duplicate layer definition: " + definition.id.name());
            }
        }
        definitions = List.copyOf(unique.values());
        overlapPolicy = builder.overlapPolicy;
        if (definitions.isEmpty()) throw new IllegalArgumentException("At least one layer is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    public LayerModel build(TypeModelResult model) {
        Objects.requireNonNull(model, "model");
        TreeMap<TypeId, TreeSet<LayerId>> candidates = new TreeMap<>();
        model.types().forEach(type -> candidates.put(typeId(type), new TreeSet<>()));
        for (Definition definition : definitions) {
            definition.selector.selectFrom(model).selected().forEach(type ->
                    candidates.get(typeId(type)).add(definition.id));
        }
        TreeMap<TypeId, LayerId> memberships = new TreeMap<>();
        List<TypeId> unassigned = new ArrayList<>();
        candidates.forEach((type, offered) -> {
            if (offered.isEmpty()) {
                unassigned.add(type);
            } else if (offered.size() == 1 || overlapPolicy == LayerOverlapPolicy.FIRST_BY_NAME) {
                memberships.put(type, offered.getFirst());
            } else {
                throw new LayerMembershipException(
                        "Type " + type.stableKey() + " matches multiple layers " + offered);
            }
        });
        TreeMap<LayerId, List<TypeId>> typesByLayer = new TreeMap<>();
        definitions.forEach(definition -> typesByLayer.put(definition.id, new ArrayList<>()));
        memberships.forEach((type, layer) -> typesByLayer.get(layer).add(type));
        List<JavaLayer> layers = definitions.stream()
                .map(definition -> new JavaLayer(
                        definition.id, definition.presence, typesByLayer.get(definition.id)))
                .toList();
        return new LayerModel(layers, memberships, unassigned, definitionKey());
    }

    private String definitionKey() {
        return "definitions=" + definitions.stream().map(Definition::key).toList()
                + ";overlap=" + overlapPolicy;
    }

    public static final class Builder {
        private final List<Definition> definitions = new ArrayList<>();
        private LayerOverlapPolicy overlapPolicy = LayerOverlapPolicy.FAIL;

        public Builder required(String name, TypeSelector selector) {
            definitions.add(new Definition(
                    LayerId.named(name), LayerPresence.REQUIRED,
                    Objects.requireNonNull(selector, "selector")));
            return this;
        }

        public Builder optional(String name, TypeSelector selector) {
            definitions.add(new Definition(
                    LayerId.named(name), LayerPresence.OPTIONAL,
                    Objects.requireNonNull(selector, "selector")));
            return this;
        }

        public Builder overlapPolicy(LayerOverlapPolicy policy) {
            overlapPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public LayerDefinitions create() {
            return new LayerDefinitions(this);
        }

        public LayerModel build(TypeModelResult model) {
            return create().build(model);
        }
    }

    private record Definition(LayerId id, LayerPresence presence, TypeSelector selector) {
        private Definition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(presence, "presence");
            Objects.requireNonNull(selector, "selector");
        }

        private String key() {
            return id.stableKey() + ":" + presence + "=" + selector.description().text();
        }
    }

    private static TypeId typeId(JavaType type) {
        return TypeId.ofBinaryName(type.binaryName());
    }
}
