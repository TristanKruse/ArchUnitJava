package dev.archunitjava.graph;

/** A JVM type identified by its Java binary name (for example {@code p.Outer$Inner}). */
public record TypeId(String binaryName) implements StableId {
    public TypeId { Names.binaryName(binaryName); }
    public static TypeId ofBinaryName(String name) { return new TypeId(Names.binaryName(name)); }
    @Override public String stableKey() { return "type:" + binaryName; }
}
