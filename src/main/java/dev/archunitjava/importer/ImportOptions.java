package dev.archunitjava.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable import semantics shared by directory and archive inputs.
 *
 * <p>The scope is a hard boundary. Within it resources are included initially, then root
 * {@code .archignore} rules are applied in file order, followed by configured rules in list order.
 * The last matching rule wins; a leading {@code !} in {@code .archignore} is an include rule.
 */
public record ImportOptions(
        ImportScope scope,
        List<ImportResourceRule> rules,
        boolean readArchIgnore,
        int maximumIgnoreBytes,
        int maximumIgnoreLines) {
    public static final int DEFAULT_MAXIMUM_IGNORE_BYTES = 64 * 1024;
    public static final int DEFAULT_MAXIMUM_IGNORE_LINES = 4096;

    public ImportOptions {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(rules, "rules");
        rules = rules.stream()
                .map(value -> Objects.requireNonNull(value, "rule"))
                .toList();
        if (maximumIgnoreBytes < 1) {
            throw new IllegalArgumentException("maximumIgnoreBytes must be positive");
        }
        if (maximumIgnoreLines < 1) {
            throw new IllegalArgumentException("maximumIgnoreLines must be positive");
        }
    }

    public static ImportOptions defaults() {
        return new ImportOptions(
                ImportScope.all(),
                List.of(),
                true,
                DEFAULT_MAXIMUM_IGNORE_BYTES,
                DEFAULT_MAXIMUM_IGNORE_LINES);
    }

    public ImportOptions withScope(ImportScope value) {
        return new ImportOptions(value, rules, readArchIgnore, maximumIgnoreBytes, maximumIgnoreLines);
    }

    public ImportOptions withRule(ImportResourceRule value) {
        ArrayList<ImportResourceRule> updated = new ArrayList<>(rules);
        updated.add(Objects.requireNonNull(value, "value"));
        return new ImportOptions(scope, updated, readArchIgnore, maximumIgnoreBytes, maximumIgnoreLines);
    }

    public ImportOptions withoutArchIgnore() {
        return new ImportOptions(scope, rules, false, maximumIgnoreBytes, maximumIgnoreLines);
    }

    /** Stable material suitable for inclusion in cache keys. */
    public String fingerprintMaterial() {
        StringBuilder result = new StringBuilder("scope=").append(scope.name()).append(';');
        scope.resourcePatterns().forEach(pattern -> result.append("scope-pattern=")
                .append(pattern.description()).append(';'));
        result.append("archignore=").append(readArchIgnore)
                .append(";ignore-bytes=").append(maximumIgnoreBytes)
                .append(";ignore-lines=").append(maximumIgnoreLines).append(';');
        rules.forEach(rule -> result.append("rule=")
                .append(rule.action()).append(':')
                .append(rule.pattern().description()).append(':')
                .append(rule.source()).append(':')
                .append(rule.line()).append(';'));
        return result.toString();
    }
}
