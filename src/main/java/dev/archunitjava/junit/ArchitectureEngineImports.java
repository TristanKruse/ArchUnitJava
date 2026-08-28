package dev.archunitjava.junit;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Execution-scoped cache hook for expensive, immutable architecture imports. */
public final class ArchitectureEngineImports {
    private static final ThreadLocal<ExecutionCache> CURRENT = new ThreadLocal<>();

    private ArchitectureEngineImports() {}

    public static <T> T cached(String key, Supplier<T> importer) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("cache key must not be blank");
        }
        Objects.requireNonNull(importer, "importer");
        ExecutionCache cache = CURRENT.get();
        if (cache == null) {
            throw new IllegalStateException(
                    "Architecture import caching is only available during engine execution");
        }
        return cache.value(key, importer);
    }

    static <T> T withCache(ExecutionCache cache, Supplier<T> action) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested architecture-engine execution is not supported");
        }
        CURRENT.set(Objects.requireNonNull(cache, "cache"));
        try {
            return action.get();
        } finally {
            CURRENT.remove();
        }
    }

    static final class ExecutionCache {
        private final boolean enabled;
        private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();

        ExecutionCache(boolean enabled) {
            this.enabled = enabled;
        }

        @SuppressWarnings("unchecked")
        <T> T value(String key, Supplier<T> importer) {
            if (!enabled) return Objects.requireNonNull(importer.get(), "import result");
            return (T) values.computeIfAbsent(
                    key, ignored -> Objects.requireNonNull(importer.get(), "import result"));
        }
    }
}
