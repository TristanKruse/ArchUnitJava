package dev.archunitjava.graph;

/** A relative, forward-slash-separated class-path resource location. */
public record LocationId(String resourcePath) implements StableId {
    public LocationId {
        if (resourcePath == null || resourcePath.isBlank() || resourcePath.startsWith("/")
                || resourcePath.indexOf('\\') >= 0 || resourcePath.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Invalid resource path: " + resourcePath);
        }
        for (String part : resourcePath.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid resource path: " + resourcePath);
            }
        }
    }
    public static LocationId ofResourcePath(String path) { return new LocationId(path); }
    @Override public String stableKey() { return "location:" + resourcePath; }
}
