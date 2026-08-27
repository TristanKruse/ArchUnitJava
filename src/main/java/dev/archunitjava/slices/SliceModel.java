package dev.archunitjava.slices;

import dev.archunitjava.graph.TypeId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable, non-overlapping slice memberships plus explicitly retained unmatched types. */
public final class SliceModel {
    private final List<JavaSlice> slices;
    private final Map<TypeId, SliceId> memberships;
    private final List<TypeId> unmatchedTypes;
    private final String definitionKey;

    SliceModel(
            Collection<JavaSlice> slices,
            Map<TypeId, SliceId> memberships,
            Collection<TypeId> unmatchedTypes,
            String definitionKey) {
        Objects.requireNonNull(slices, "slices");
        this.slices = slices.stream()
                .map(value -> Objects.requireNonNull(value, "slice"))
                .distinct().sorted().toList();
        Objects.requireNonNull(memberships, "memberships");
        TreeMap<TypeId, SliceId> copy = new TreeMap<>();
        memberships.forEach((type, slice) -> copy.put(
                Objects.requireNonNull(type, "type"), Objects.requireNonNull(slice, "slice")));
        this.memberships = Map.copyOf(copy);
        this.unmatchedTypes = unmatchedTypes.stream()
                .map(value -> Objects.requireNonNull(value, "unmatchedType"))
                .distinct().sorted().toList();
        if (definitionKey == null || definitionKey.isBlank()) {
            throw new IllegalArgumentException("definitionKey must not be blank");
        }
        this.definitionKey = definitionKey;
        validate();
    }

    public List<JavaSlice> slices() {
        return slices;
    }

    public Optional<SliceId> sliceOf(TypeId type) {
        return Optional.ofNullable(memberships.get(Objects.requireNonNull(type, "type")));
    }

    public List<TypeId> unmatchedTypes() {
        return unmatchedTypes;
    }

    public String definitionKey() {
        return definitionKey;
    }

    private void validate() {
        TreeMap<TypeId, SliceId> fromSlices = new TreeMap<>();
        for (JavaSlice slice : slices) {
            for (TypeId type : slice.types()) {
                SliceId previous = fromSlices.putIfAbsent(type, slice.id());
                if (previous != null) throw new IllegalArgumentException("Overlapping slice memberships");
            }
        }
        if (!fromSlices.equals(new TreeMap<>(memberships))) {
            throw new IllegalArgumentException("Membership index disagrees with slices");
        }
        if (unmatchedTypes.stream().anyMatch(memberships::containsKey)) {
            throw new IllegalArgumentException("Matched type also appears as unmatched");
        }
    }
}
