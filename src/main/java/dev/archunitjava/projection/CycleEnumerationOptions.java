package dev.archunitjava.projection;

/** Explicit resource bounds for optional elementary-cycle diagnostics. */
public record CycleEnumerationOptions(
        boolean enabled, int maximumCycles, int maximumCycleLength, long maximumTraversedEdges) {
    private static final int DEFAULT_MAXIMUM_CYCLES = 100;
    private static final int DEFAULT_MAXIMUM_CYCLE_LENGTH = 100;
    private static final long DEFAULT_MAXIMUM_TRAVERSED_EDGES = 100_000;

    public CycleEnumerationOptions {
        if (maximumCycles < 0) throw new IllegalArgumentException("maximumCycles must not be negative");
        if (maximumCycleLength < 0) {
            throw new IllegalArgumentException("maximumCycleLength must not be negative");
        }
        if (maximumTraversedEdges < 0) {
            throw new IllegalArgumentException("maximumTraversedEdges must not be negative");
        }
        if (enabled && (maximumCycles == 0
                || maximumCycleLength == 0
                || maximumTraversedEdges == 0)) {
            throw new IllegalArgumentException("Enabled enumeration bounds must be positive");
        }
    }

    /** Conservative defaults suitable for interactive diagnostics. */
    public static CycleEnumerationOptions defaults() {
        return new CycleEnumerationOptions(
                true,
                DEFAULT_MAXIMUM_CYCLES,
                DEFAULT_MAXIMUM_CYCLE_LENGTH,
                DEFAULT_MAXIMUM_TRAVERSED_EDGES);
    }

    public static CycleEnumerationOptions bounded(
            int maximumCycles, int maximumCycleLength, long maximumTraversedEdges) {
        return new CycleEnumerationOptions(
                true, maximumCycles, maximumCycleLength, maximumTraversedEdges);
    }

    public static CycleEnumerationOptions componentsOnly() {
        return new CycleEnumerationOptions(false, 0, 0, 0);
    }
}
