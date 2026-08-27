package dev.archunitjava.slices;

import dev.archunitjava.graph.StableId;

/** Stable identity of one architecture slice. */
public record SliceId(String name) implements StableId {
    public SliceId {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0
                || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Slice name must be non-blank single-line text");
        }
    }

    public static SliceId named(String name) {
        return new SliceId(name);
    }

    @Override
    public String stableKey() {
        return "slice:" + name;
    }
}
