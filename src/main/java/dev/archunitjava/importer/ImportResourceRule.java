package dev.archunitjava.importer;

import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.util.Objects;

/** One immutable, reviewable include/exclude rule for a root-relative resource path. */
public record ImportResourceRule(
        ImportRuleAction action, JavaPattern pattern, String source, int line) {
    public ImportResourceRule {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(pattern, "pattern");
        if (pattern.description().domain() != PatternDomain.RESOURCE_PATH) {
            throw new IllegalArgumentException("import rules require RESOURCE_PATH patterns");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (line < 0) throw new IllegalArgumentException("line must not be negative");
    }

    public static ImportResourceRule include(String glob) {
        return new ImportResourceRule(
                ImportRuleAction.INCLUDE,
                JavaPattern.glob(PatternDomain.RESOURCE_PATH, glob),
                "configuration",
                0);
    }

    public static ImportResourceRule exclude(String glob) {
        return new ImportResourceRule(
                ImportRuleAction.EXCLUDE,
                JavaPattern.glob(PatternDomain.RESOURCE_PATH, glob),
                "configuration",
                0);
    }

    public boolean matches(String rootRelativeResource) {
        return pattern.matches(rootRelativeResource);
    }

    public String description() {
        return (action == ImportRuleAction.INCLUDE ? "!" : "")
                + pattern.description().expression();
    }
}
