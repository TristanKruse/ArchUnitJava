package dev.archunitjava.layers;

import dev.archunitjava.selector.SelectorDescription;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Immutable exact-name layer selection. */
public final class LayerSelector {
    private final SelectorDescription description;
    private final Set<LayerId> ids;
    private final boolean all;
    private final boolean intentionalEmpty;

    private LayerSelector(
            SelectorDescription description, Set<LayerId> ids, boolean all, boolean intentionalEmpty) {
        this.description = Objects.requireNonNull(description, "description");
        this.ids = Set.copyOf(new TreeSet<>(Objects.requireNonNull(ids, "ids")));
        this.all = all;
        this.intentionalEmpty = intentionalEmpty;
    }

    public static LayerSelector all() {
        return new LayerSelector(new SelectorDescription("all layers"), Set.of(), true, false);
    }

    public static LayerSelector none() {
        return new LayerSelector(new SelectorDescription("no layers"), Set.of(), false, true);
    }

    public static LayerSelector named(String name) {
        return names(List.of(name));
    }

    public static LayerSelector names(Collection<String> names) {
        TreeSet<LayerId> values = new TreeSet<>();
        Objects.requireNonNull(names, "names").forEach(name -> values.add(LayerId.named(name)));
        if (values.isEmpty()) throw new IllegalArgumentException("Layer names must not be empty");
        return new LayerSelector(
                new SelectorDescription("layers named " + values.stream()
                        .map(LayerId::name).toList()),
                values,
                false,
                false);
    }

    public SelectorDescription description() {
        return description;
    }

    public List<JavaLayer> selectFrom(LayerModel model) {
        return Objects.requireNonNull(model, "model").layers().stream()
                .filter(layer -> all || ids.contains(layer.id()))
                .toList();
    }

    public boolean intentionalEmpty() {
        return intentionalEmpty;
    }
}
