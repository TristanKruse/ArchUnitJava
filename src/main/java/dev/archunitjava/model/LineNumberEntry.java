package dev.archunitjava.model;

/** One exact line-number-table transition. */
public record LineNumberEntry(int bytecodeOffset, int lineNumber)
        implements Comparable<LineNumberEntry> {
    public LineNumberEntry {
        if (bytecodeOffset < 0) throw new IllegalArgumentException("bytecodeOffset must not be negative");
        if (lineNumber < 0) throw new IllegalArgumentException("lineNumber must not be negative");
    }

    @Override
    public int compareTo(LineNumberEntry other) {
        int result = Integer.compare(bytecodeOffset, other.bytecodeOffset);
        return result != 0 ? result : Integer.compare(lineNumber, other.lineNumber);
    }
}
