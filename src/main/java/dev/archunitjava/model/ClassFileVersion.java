package dev.archunitjava.model;

/** Lossless JVM class-file version tuple. */
public record ClassFileVersion(int major, int minor) implements Comparable<ClassFileVersion> {
    public ClassFileVersion {
        if (major < 0 || minor < 0) {
            throw new IllegalArgumentException("class-file versions must not be negative");
        }
    }

    @Override
    public int compareTo(ClassFileVersion other) {
        int result = Integer.compare(major, other.major);
        return result != 0 ? result : Integer.compare(minor, other.minor);
    }
}
