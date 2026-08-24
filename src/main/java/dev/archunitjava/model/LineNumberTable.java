package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeSet;

/** Immutable deterministic line table preserving repeated lines and offsets. */
public final class LineNumberTable {
    private final List<LineNumberEntry> entries;

    public LineNumberTable(List<LineNumberEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        TreeSet<LineNumberEntry> sorted = new TreeSet<>();
        for (LineNumberEntry entry : entries) {
            sorted.add(Objects.requireNonNull(entry, "entry"));
        }
        this.entries = List.copyOf(sorted);
    }

    public static LineNumberTable empty() {
        return new LineNumberTable(List.of());
    }

    public List<LineNumberEntry> entries() {
        return entries;
    }

    /** Resolves the greatest preceding offset; ties choose the smallest line deterministically. */
    public OptionalInt lineAt(int bytecodeOffset) {
        if (bytecodeOffset < 0) throw new IllegalArgumentException("bytecodeOffset must not be negative");
        LineNumberEntry best = null;
        for (LineNumberEntry entry : entries) {
            if (entry.bytecodeOffset() > bytecodeOffset) break;
            if (best == null || entry.bytecodeOffset() > best.bytecodeOffset()) best = entry;
        }
        return best == null ? OptionalInt.empty() : OptionalInt.of(best.lineNumber());
    }
}
