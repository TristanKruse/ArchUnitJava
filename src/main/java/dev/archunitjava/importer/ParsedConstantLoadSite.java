package dev.archunitjava.importer;

/** Member and bytecode offset that loaded a constant-pool entry. */
public record ParsedConstantLoadSite(String memberName, String memberDescriptor, int bytecodeOffset)
        implements Comparable<ParsedConstantLoadSite> {
    public ParsedConstantLoadSite {
        if (memberName == null || memberName.isBlank()) {
            throw new IllegalArgumentException("memberName must not be blank");
        }
        if (memberDescriptor == null || memberDescriptor.isBlank()) {
            throw new IllegalArgumentException("memberDescriptor must not be blank");
        }
        if (bytecodeOffset < 0) throw new IllegalArgumentException("bytecodeOffset must not be negative");
    }

    @Override
    public int compareTo(ParsedConstantLoadSite other) {
        int result = memberName.compareTo(other.memberName);
        if (result != 0) return result;
        result = memberDescriptor.compareTo(other.memberDescriptor);
        return result != 0 ? result : Integer.compare(bytecodeOffset, other.bytecodeOffset);
    }
}
