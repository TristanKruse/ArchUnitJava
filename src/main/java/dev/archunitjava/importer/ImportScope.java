package dev.archunitjava.importer;

import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.util.List;
import java.util.Objects;

/** Hard import boundary; later include rules cannot escape it. */
public record ImportScope(String name, List<JavaPattern> resourcePatterns) {
    public ImportScope {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        Objects.requireNonNull(resourcePatterns, "resourcePatterns");
        resourcePatterns = resourcePatterns.stream()
                .map(pattern -> {
                    Objects.requireNonNull(pattern, "resourcePattern");
                    if (pattern.description().domain() != PatternDomain.RESOURCE_PATH) {
                        throw new IllegalArgumentException("scope patterns require RESOURCE_PATH domain");
                    }
                    return pattern;
                })
                .toList();
    }

    public static ImportScope all() {
        return new ImportScope("all", List.of());
    }

    public static ImportScope matching(String name, String... globs) {
        return new ImportScope(
                name,
                java.util.Arrays.stream(globs)
                        .map(glob -> JavaPattern.glob(PatternDomain.RESOURCE_PATH, glob))
                        .toList());
    }

    public boolean includes(String resourceName) {
        Objects.requireNonNull(resourceName, "resourceName");
        return resourcePatterns.isEmpty() || resourcePatterns.stream().anyMatch(rule -> rule.matches(resourceName));
    }
}
