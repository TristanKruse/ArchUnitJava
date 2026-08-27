package dev.archunitjava.layers;

import dev.archunitjava.graph.TypeId;
import java.util.List;
import java.util.Objects;

/** One immutable named layer; only optional layers may be empty. */
public record JavaLayer(LayerId id, LayerPresence presence, List<TypeId> types)
        implements Comparable<JavaLayer> {
    public JavaLayer {
        Objects.requireNonNull(id, "id");
        if (id.isUnassigned()) throw new IllegalArgumentException("Unassigned is not a declared layer");
        Objects.requireNonNull(presence, "presence");
        Objects.requireNonNull(types, "types");
        types = types.stream()
                .map(value -> Objects.requireNonNull(value, "type"))
                .distinct().sorted().toList();
        if (presence == LayerPresence.REQUIRED && types.isEmpty()) {
            throw new LayerMembershipException("Required layer is empty: " + id.name());
        }
    }

    @Override
    public int compareTo(JavaLayer other) {
        return id.compareTo(other.id);
    }
}
