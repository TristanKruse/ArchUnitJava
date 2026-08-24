package dev.archunitjava.graph;

/** A named JPMS module. */
public record ModuleId(String name) implements StableId {
    public ModuleId { Names.qualifiedJavaName(name, "module name"); }
    public static ModuleId named(String name) { return new ModuleId(Names.qualifiedJavaName(name, "module name")); }
    @Override public String stableKey() { return "module:" + name; }
}
