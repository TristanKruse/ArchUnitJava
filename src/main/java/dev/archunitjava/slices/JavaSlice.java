package dev.archunitjava.slices;

import dev.archunitjava.graph.TypeId;
import java.util.List;
import java.util.Objects;

/** One non-empty, immutable, deterministically ordered type group. */
public record JavaSlice(SliceId id, List<TypeId> types) implements Comparable<JavaSlice> {
    public JavaSlice {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(types, "types");
        types = types.stream()
                .map(value -> Objects.requireNonNull(value, "type"))
                .distinct().sorted().toList();
        if (types.isEmpty()) throw new IllegalArgumentException("Slice must contain at least one type");
    }

    @Override
    public int compareTo(JavaSlice other) {
        return id.compareTo(other.id);
    }
}
