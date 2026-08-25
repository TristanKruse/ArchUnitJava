package dev.archunitjava.importer;

import java.util.Objects;

/** Explicit lookup mode, traversal bounds, and opt-in manifest expansion policy. */
public record ClassPathAssemblyOptions(
        ClassPathAssemblyMode mode,
        boolean followManifestClassPath,
        int maximumManifestClassPathEntries,
        int maximumManifestDepth,
        int maximumManifestBytes,
        InputEnumerationOptions enumerationOptions) {
    public static final int DEFAULT_MAXIMUM_MANIFEST_ENTRIES = 256;
    public static final int DEFAULT_MAXIMUM_MANIFEST_DEPTH = 8;
    public static final int DEFAULT_MAXIMUM_MANIFEST_BYTES = 64 * 1024;

    public ClassPathAssemblyOptions {
        Objects.requireNonNull(mode, "mode");
        if (maximumManifestClassPathEntries < 1) {
            throw new IllegalArgumentException("maximumManifestClassPathEntries must be positive");
        }
        if (maximumManifestDepth < 1) {
            throw new IllegalArgumentException("maximumManifestDepth must be positive");
        }
        if (maximumManifestBytes < 1) {
            throw new IllegalArgumentException("maximumManifestBytes must be positive");
        }
        Objects.requireNonNull(enumerationOptions, "enumerationOptions");
    }

    public static ClassPathAssemblyOptions classPathDefaults() {
        return new ClassPathAssemblyOptions(
                ClassPathAssemblyMode.CLASSPATH,
                false,
                DEFAULT_MAXIMUM_MANIFEST_ENTRIES,
                DEFAULT_MAXIMUM_MANIFEST_DEPTH,
                DEFAULT_MAXIMUM_MANIFEST_BYTES,
                InputEnumerationOptions.defaults());
    }

    public static ClassPathAssemblyOptions modulePathDefaults() {
        return new ClassPathAssemblyOptions(
                ClassPathAssemblyMode.MODULE_PATH,
                false,
                DEFAULT_MAXIMUM_MANIFEST_ENTRIES,
                DEFAULT_MAXIMUM_MANIFEST_DEPTH,
                DEFAULT_MAXIMUM_MANIFEST_BYTES,
                InputEnumerationOptions.defaults());
    }

    public ClassPathAssemblyOptions withManifestClassPath(boolean enabled) {
        return new ClassPathAssemblyOptions(
                mode,
                enabled,
                maximumManifestClassPathEntries,
                maximumManifestDepth,
                maximumManifestBytes,
                enumerationOptions);
    }
}
