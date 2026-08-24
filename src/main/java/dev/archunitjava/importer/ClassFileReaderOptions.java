package dev.archunitjava.importer;

/** Immutable safety bounds for reading individual class resources. */
public record ClassFileReaderOptions(int maximumClassBytes) {
    public static final int DEFAULT_MAXIMUM_CLASS_BYTES = 16 * 1024 * 1024;

    public ClassFileReaderOptions {
        if (maximumClassBytes < 1) {
            throw new IllegalArgumentException("maximumClassBytes must be positive");
        }
    }

    public static ClassFileReaderOptions defaults() {
        return new ClassFileReaderOptions(DEFAULT_MAXIMUM_CLASS_BYTES);
    }
}
