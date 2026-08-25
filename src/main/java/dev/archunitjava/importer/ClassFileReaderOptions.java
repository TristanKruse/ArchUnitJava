package dev.archunitjava.importer;

/** Immutable safety bounds for reading individual class resources. */
public record ClassFileReaderOptions(int maximumClassBytes, int maximumDiagnostics) {
    public static final int DEFAULT_MAXIMUM_CLASS_BYTES = 16 * 1024 * 1024;
    public static final int DEFAULT_MAXIMUM_DIAGNOSTICS = 10_000;

    public ClassFileReaderOptions {
        if (maximumClassBytes < 1) {
            throw new IllegalArgumentException("maximumClassBytes must be positive");
        }
        if (maximumDiagnostics < 1) {
            throw new IllegalArgumentException("maximumDiagnostics must be positive");
        }
    }

    public ClassFileReaderOptions(int maximumClassBytes) {
        this(maximumClassBytes, DEFAULT_MAXIMUM_DIAGNOSTICS);
    }

    public static ClassFileReaderOptions defaults() {
        return new ClassFileReaderOptions(DEFAULT_MAXIMUM_CLASS_BYTES, DEFAULT_MAXIMUM_DIAGNOSTICS);
    }
}
