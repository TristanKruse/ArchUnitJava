package dev.archunitjava.graph;

/**
 * An identifier with a typed, deterministic representation suitable for ordering.
 * Implementations must use the stable key as identity: equal keys must represent equal identifiers.
 */
public interface StableId extends Comparable<StableId> {
    String stableKey();

    @Override
    default int compareTo(StableId other) {
        return stableKey().compareTo(other.stableKey());
    }
}
