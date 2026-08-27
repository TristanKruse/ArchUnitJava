package dev.archunitjava.presets;

import dev.archunitjava.layers.LayerId;
import dev.archunitjava.selector.TypeSelector;
import java.util.Objects;

/** One explicitly named layer selector in a transparent architecture preset. */
public record PresetLayer(String name, TypeSelector selector) implements Comparable<PresetLayer> {
    public PresetLayer {
        name = LayerId.named(name).name();
        Objects.requireNonNull(selector, "selector");
    }

    public static PresetLayer named(String name, TypeSelector selector) {
        return new PresetLayer(name, selector);
    }

    @Override
    public int compareTo(PresetLayer other) {
        return name.compareTo(other.name);
    }
}
