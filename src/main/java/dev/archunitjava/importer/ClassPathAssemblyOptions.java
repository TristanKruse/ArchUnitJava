package dev.archunitjava.importer;

import java.util.Objects;

/** Explicit lookup mode, traversal bounds, and opt-in manifest expansion policy. */
public record ClassPathAssemblyOptions(
        ClassPathAssemblyMode mode,
        boolean followManifestClassPath,
        int maximumManifestClassPathEntries,
        int maximumManifestDepth,
        int maximumManifestBytes,
        int targetJavaRelease,
        InputEnumerationOptions enumerationOptions,
        ImportOptions importOptions) {
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
        if (targetJavaRelease < 1) {
            throw new IllegalArgumentException("targetJavaRelease must be positive");
        }
        Objects.requireNonNull(enumerationOptions, "enumerationOptions");
        Objects.requireNonNull(importOptions, "importOptions");
    }

    public ClassPathAssemblyOptions(
            ClassPathAssemblyMode mode,
            boolean followManifestClassPath,
            int maximumManifestClassPathEntries,
            int maximumManifestDepth,
            int maximumManifestBytes,
            int targetJavaRelease,
            InputEnumerationOptions enumerationOptions) {
        this(
                mode,
                followManifestClassPath,
                maximumManifestClassPathEntries,
                maximumManifestDepth,
                maximumManifestBytes,
                targetJavaRelease,
                enumerationOptions,
                ImportOptions.defaults());
    }

    public ClassPathAssemblyOptions(
            ClassPathAssemblyMode mode,
            boolean followManifestClassPath,
            int maximumManifestClassPathEntries,
            int maximumManifestDepth,
            int maximumManifestBytes,
            InputEnumerationOptions enumerationOptions) {
        this(
                mode,
                followManifestClassPath,
                maximumManifestClassPathEntries,
                maximumManifestDepth,
                maximumManifestBytes,
                Runtime.version().feature(),
                enumerationOptions,
                ImportOptions.defaults());
    }

    public static ClassPathAssemblyOptions classPathDefaults() {
        return new ClassPathAssemblyOptions(
                ClassPathAssemblyMode.CLASSPATH,
                false,
                DEFAULT_MAXIMUM_MANIFEST_ENTRIES,
                DEFAULT_MAXIMUM_MANIFEST_DEPTH,
                DEFAULT_MAXIMUM_MANIFEST_BYTES,
                Runtime.version().feature(),
                InputEnumerationOptions.defaults(),
                ImportOptions.defaults());
    }

    public static ClassPathAssemblyOptions modulePathDefaults() {
        return new ClassPathAssemblyOptions(
                ClassPathAssemblyMode.MODULE_PATH,
                false,
                DEFAULT_MAXIMUM_MANIFEST_ENTRIES,
                DEFAULT_MAXIMUM_MANIFEST_DEPTH,
                DEFAULT_MAXIMUM_MANIFEST_BYTES,
                Runtime.version().feature(),
                InputEnumerationOptions.defaults(),
                ImportOptions.defaults());
    }

    public ClassPathAssemblyOptions withManifestClassPath(boolean enabled) {
        return new ClassPathAssemblyOptions(
                mode,
                enabled,
                maximumManifestClassPathEntries,
                maximumManifestDepth,
                maximumManifestBytes,
                targetJavaRelease,
                enumerationOptions,
                importOptions);
    }

    public ClassPathAssemblyOptions withTargetJavaRelease(int release) {
        return new ClassPathAssemblyOptions(
                mode,
                followManifestClassPath,
                maximumManifestClassPathEntries,
                maximumManifestDepth,
                maximumManifestBytes,
                release,
                enumerationOptions,
                importOptions);
    }

    public ClassPathAssemblyOptions withImportOptions(ImportOptions value) {
        return new ClassPathAssemblyOptions(
                mode,
                followManifestClassPath,
                maximumManifestClassPathEntries,
                maximumManifestDepth,
                maximumManifestBytes,
                targetJavaRelease,
                enumerationOptions,
                value);
    }
}
