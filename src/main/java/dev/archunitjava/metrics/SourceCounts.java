package dev.archunitjava.metrics;

/** Aggregate counts; missing source never removes an otherwise included type or member. */
public record SourceCounts(
        long packageCount,
        long typeCount,
        long memberCount,
        long fieldCount,
        long methodCount,
        long constructorCount,
        long staticInitializerCount,
        long recordComponentCount,
        long sourceFileCount,
        long physicalLineCount,
        long blankLineCount,
        long commentLineCount,
        long codeLineCount,
        long missingSourceTypeCount) {
    public SourceCounts {
        long[] values = {packageCount, typeCount, memberCount, fieldCount, methodCount,
                constructorCount, staticInitializerCount, recordComponentCount, sourceFileCount,
                physicalLineCount, blankLineCount, commentLineCount, codeLineCount,
                missingSourceTypeCount};
        for (long value : values) {
            if (value < 0) throw new IllegalArgumentException("source counts must not be negative");
        }
        if (memberCount != fieldCount + methodCount + constructorCount + staticInitializerCount) {
            throw new IllegalArgumentException("member categories must partition member count");
        }
        if (physicalLineCount != blankLineCount + commentLineCount + codeLineCount) {
            throw new IllegalArgumentException("line categories must partition physical lines");
        }
        if (missingSourceTypeCount > typeCount) {
            throw new IllegalArgumentException("missing source types cannot exceed type count");
        }
    }
}
