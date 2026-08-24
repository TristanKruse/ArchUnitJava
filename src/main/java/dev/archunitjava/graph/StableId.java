package dev.archunitjava.graph;

/** An identifier with a typed, deterministic representation suitable for ordering. */
public interface StableId extends Comparable<StableId> {
    String stableKey();

    @Override
    default int compareTo(StableId other) {
        return stableKey().compareTo(other.stableKey());
    }
}
