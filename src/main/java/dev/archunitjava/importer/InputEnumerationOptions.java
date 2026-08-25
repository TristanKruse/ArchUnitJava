package dev.archunitjava.importer;

/** Immutable safety bounds for class-file input traversal. */
public record InputEnumerationOptions(
        int maximumInputs,
        int maximumResourcesPerInput,
        int maximumDirectoryDepth,
        int maximumDirectoryEntries,
        int maximumArchiveEntries,
        long maximumArchiveBytes,
        long maximumArchiveUncompressedBytes,
        int maximumCompressionRatio,
        int maximumArchiveNestingDepth,
        int maximumResourceNameCharacters,
        int maximumDiagnostics) {
    public static final int DEFAULT_MAXIMUM_INPUTS = 4096;
    public static final int DEFAULT_MAXIMUM_RESOURCES = 100_000;
    public static final int DEFAULT_MAXIMUM_DIRECTORY_DEPTH = 64;
    public static final int DEFAULT_MAXIMUM_DIRECTORY_ENTRIES = 100_000;
    public static final int DEFAULT_MAXIMUM_ARCHIVE_ENTRIES = 100_000;
    public static final long DEFAULT_MAXIMUM_ARCHIVE_BYTES = 1024L * 1024 * 1024;
    public static final long DEFAULT_MAXIMUM_ARCHIVE_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024;
    public static final int DEFAULT_MAXIMUM_COMPRESSION_RATIO = 200;
    public static final int DEFAULT_MAXIMUM_ARCHIVE_NESTING_DEPTH = 0;
    public static final int DEFAULT_MAXIMUM_RESOURCE_NAME_CHARACTERS = 4096;
    public static final int DEFAULT_MAXIMUM_DIAGNOSTICS = 10_000;

    public InputEnumerationOptions {
        if (maximumInputs < 1) {
            throw new IllegalArgumentException("maximumInputs must be positive");
        }
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
        if (maximumArchiveBytes < 1 || maximumArchiveUncompressedBytes < 1) {
            throw new IllegalArgumentException("archive byte limits must be positive");
        }
        if (maximumCompressionRatio < 1) {
            throw new IllegalArgumentException("maximumCompressionRatio must be positive");
        }
        if (maximumArchiveNestingDepth < 0) {
            throw new IllegalArgumentException("maximumArchiveNestingDepth must not be negative");
        }
        if (maximumResourceNameCharacters < 1 || maximumDiagnostics < 1) {
            throw new IllegalArgumentException("name and diagnostic limits must be positive");
        }
    }

    public InputEnumerationOptions(
            int maximumResourcesPerInput,
            int maximumDirectoryDepth,
            int maximumDirectoryEntries,
            int maximumArchiveEntries) {
        this(
                DEFAULT_MAXIMUM_INPUTS,
                maximumResourcesPerInput,
                maximumDirectoryDepth,
                maximumDirectoryEntries,
                maximumArchiveEntries,
                DEFAULT_MAXIMUM_ARCHIVE_BYTES,
                DEFAULT_MAXIMUM_ARCHIVE_UNCOMPRESSED_BYTES,
                DEFAULT_MAXIMUM_COMPRESSION_RATIO,
                DEFAULT_MAXIMUM_ARCHIVE_NESTING_DEPTH,
                DEFAULT_MAXIMUM_RESOURCE_NAME_CHARACTERS,
                DEFAULT_MAXIMUM_DIAGNOSTICS);
    }

    public static InputEnumerationOptions defaults() {
        return new InputEnumerationOptions(
                DEFAULT_MAXIMUM_INPUTS,
                DEFAULT_MAXIMUM_RESOURCES,
                DEFAULT_MAXIMUM_DIRECTORY_DEPTH,
                DEFAULT_MAXIMUM_DIRECTORY_ENTRIES,
                DEFAULT_MAXIMUM_ARCHIVE_ENTRIES,
                DEFAULT_MAXIMUM_ARCHIVE_BYTES,
                DEFAULT_MAXIMUM_ARCHIVE_UNCOMPRESSED_BYTES,
                DEFAULT_MAXIMUM_COMPRESSION_RATIO,
                DEFAULT_MAXIMUM_ARCHIVE_NESTING_DEPTH,
                DEFAULT_MAXIMUM_RESOURCE_NAME_CHARACTERS,
                DEFAULT_MAXIMUM_DIAGNOSTICS);
    }
}
