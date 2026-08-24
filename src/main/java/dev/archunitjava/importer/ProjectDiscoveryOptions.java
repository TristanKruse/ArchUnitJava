package dev.archunitjava.importer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Immutable bounds and caller overrides for project discovery. */
public final class ProjectDiscoveryOptions {
    public static final int DEFAULT_MAX_ANCESTOR_DEPTH = 8;
    public static final int MAX_ANCESTOR_DEPTH = 64;

    private final Optional<Path> explicitRoot;
    private final int maxAncestorDepth;

    private ProjectDiscoveryOptions(Builder builder) {
        explicitRoot = Optional.ofNullable(builder.explicitRoot)
                .map(path -> path.toAbsolutePath().normalize());
        maxAncestorDepth = builder.maxAncestorDepth;
    }

    public static ProjectDiscoveryOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Path> explicitRoot() {
        return explicitRoot;
    }

    public int maxAncestorDepth() {
        return maxAncestorDepth;
    }

    public static final class Builder {
        private Path explicitRoot;
        private int maxAncestorDepth = DEFAULT_MAX_ANCESTOR_DEPTH;

        private Builder() {}

        public Builder explicitRoot(Path value) {
            explicitRoot = Objects.requireNonNull(value, "explicitRoot");
            return this;
        }

        public Builder maxAncestorDepth(int value) {
            if (value < 0 || value > MAX_ANCESTOR_DEPTH) {
                throw new IllegalArgumentException(
                        "maxAncestorDepth must be between 0 and " + MAX_ANCESTOR_DEPTH);
            }
            maxAncestorDepth = value;
            return this;
        }

        public ProjectDiscoveryOptions build() {
            return new ProjectDiscoveryOptions(this);
        }
    }
}
