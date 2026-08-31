package dev.archunitjava.baseline;

/** Resource limits applied before and during baseline JSON ingestion. */
public record BaselineReadLimits(
        int maxBytes,
        int maxDepth,
        int maxFindings,
        int maxSuppressions,
        int maxValuesPerArray,
        int maxStringCharacters) {
    public BaselineReadLimits {
        if (maxBytes < 1 || maxDepth < 1 || maxFindings < 1 || maxSuppressions < 1
                || maxValuesPerArray < 1 || maxStringCharacters < 1) {
            throw new IllegalArgumentException("Baseline read limits must be positive");
        }
    }

    public static BaselineReadLimits defaults() {
        return new BaselineReadLimits(1_048_576, 16, 10_000, 10_000, 100_000, 16_384);
    }
}
