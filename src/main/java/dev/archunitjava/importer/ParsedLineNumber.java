package dev.archunitjava.importer;

/** Backend-neutral line-table entry using an exact bytecode offset. */
public record ParsedLineNumber(int bytecodeOffset, int lineNumber)
        implements Comparable<ParsedLineNumber> {
    public ParsedLineNumber {
        if (bytecodeOffset < 0) throw new IllegalArgumentException("bytecodeOffset must not be negative");
        if (lineNumber < 0) throw new IllegalArgumentException("lineNumber must not be negative");
    }

    @Override
    public int compareTo(ParsedLineNumber other) {
        int result = Integer.compare(bytecodeOffset, other.bytecodeOffset);
        return result != 0 ? result : Integer.compare(lineNumber, other.lineNumber);
    }
}
