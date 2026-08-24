package dev.archunitjava.graph;

/** A Java package name, including an explicit value for the unnamed package. */
public record PackageId(String qualifiedName) implements StableId {
    public PackageId {
        if (qualifiedName == null) throw new IllegalArgumentException("Package name must not be null");
        if (!qualifiedName.isEmpty()) Names.qualifiedJavaName(qualifiedName, "package name");
    }

    public static PackageId named(String name) {
        return new PackageId(Names.qualifiedJavaName(name, "package name"));
    }

    public static PackageId unnamed() {
        return new PackageId("");
    }

    public boolean isUnnamed() {
        return qualifiedName.isEmpty();
    }

    @Override public String stableKey() { return "package:" + qualifiedName; }
}
