package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Lossless type-annotation target tag, payload, and ordered path. */
public record JavaTypeUseTarget(String targetType, String targetInfo, List<String> path) {
    public JavaTypeUseTarget {
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("targetType must not be blank");
        }
        Objects.requireNonNull(targetInfo, "targetInfo");
        Objects.requireNonNull(path, "path");
        path = List.copyOf(path);
        path.forEach(value -> Objects.requireNonNull(value, "path component"));
    }

    public String stableKey() {
        return targetType + ":" + targetInfo + ":" + String.join("/", path);
    }
}
