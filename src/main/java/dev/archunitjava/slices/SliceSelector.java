package dev.archunitjava.slices;

import dev.archunitjava.selector.SelectorDescription;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable selection of named slices for directional and pairwise rules. */
public final class SliceSelector {
    private final SelectorDescription description;
    private final Set<SliceId> ids;
    private final boolean all;

    private SliceSelector(SelectorDescription description, Set<SliceId> ids, boolean all) {
        this.description = Objects.requireNonNull(description, "description");
        this.ids = Set.copyOf(new TreeSet<>(Objects.requireNonNull(ids, "ids")));
        this.all = all;
    }

    public static SliceSelector all() {
        return new SliceSelector(new SelectorDescription("all slices"), Set.of(), true);
    }

    public static SliceSelector named(String name) {
        return names(List.of(name));
    }

    public static SliceSelector names(Collection<String> names) {
        Objects.requireNonNull(names, "names");
        TreeSet<SliceId> values = new TreeSet<>();
        names.forEach(name -> values.add(SliceId.named(name)));
        if (values.isEmpty()) throw new IllegalArgumentException("Slice names must not be empty");
        return new SliceSelector(
                new SelectorDescription("slices named " + values.stream()
                        .map(SliceId::name).toList()),
                values,
                false);
    }

    public SelectorDescription description() {
        return description;
    }

    public List<JavaSlice> selectFrom(SliceModel model) {
        Objects.requireNonNull(model, "model");
        return model.slices().stream()
                .filter(value -> all || ids.contains(value.id()))
                .toList();
    }
}
