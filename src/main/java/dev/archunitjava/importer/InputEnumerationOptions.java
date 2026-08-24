package dev.archunitjava.importer;

/** Immutable safety bounds for class-file input traversal. */
public record InputEnumerationOptions(
        int maximumResourcesPerInput,
        int maximumDirectoryDepth,
        int maximumDirectoryEntries,
        int maximumArchiveEntries) {
    public static final int DEFAULT_MAXIMUM_RESOURCES = 100_000;
    public static final int DEFAULT_MAXIMUM_DIRECTORY_DEPTH = 64;
    public static final int DEFAULT_MAXIMUM_DIRECTORY_ENTRIES = 100_000;
    public static final int DEFAULT_MAXIMUM_ARCHIVE_ENTRIES = 100_000;

    public InputEnumerationOptions {
        if (maximumResourcesPerInput < 1) {
            throw new IllegalArgumentException("maximumResourcesPerInput must be positive");
        }
        if (maximumDirectoryDepth < 1) {
            throw new IllegalArgumentException("maximumDirectoryDepth must be positive");
        }
        if (maximumDirectoryEntries < 1) {
            throw new IllegalArgumentException("maximumDirectoryEntries must be positive");
        }
        if (maximumArchiveEntries < 1) {
            throw new IllegalArgumentException("maximumArchiveEntries must be positive");
        }
    }

    public static InputEnumerationOptions defaults() {
        return new InputEnumerationOptions(
                DEFAULT_MAXIMUM_RESOURCES,
                DEFAULT_MAXIMUM_DIRECTORY_DEPTH,
                DEFAULT_MAXIMUM_DIRECTORY_ENTRIES,
                DEFAULT_MAXIMUM_ARCHIVE_ENTRIES);
    }
}
