package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** Raw nesting and nestmate attributes retained without inferring source ownership. */
public record ParsedNestingMetadata(
        List<ParsedInnerClass> innerClasses,
        List<ParsedEnclosingMethod> enclosingMethods,
        List<String> nestHostBinaryNames,
        List<String> nestMemberBinaryNames) {
    public ParsedNestingMetadata {
        innerClasses = sorted(innerClasses, "innerClass");
        enclosingMethods = sorted(enclosingMethods, "enclosingMethod");
        nestHostBinaryNames = binaryNames(nestHostBinaryNames, "nestHost");
        nestMemberBinaryNames = binaryNames(nestMemberBinaryNames, "nestMember");
    }

    public static ParsedNestingMetadata empty() {
        return new ParsedNestingMetadata(List.of(), List.of(), List.of(), List.of());
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String role) {
        Objects.requireNonNull(values, role + "es");
        return values.stream().map(value -> Objects.requireNonNull(value, role)).sorted().toList();
    }

    private static List<String> binaryNames(List<String> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream().map(value -> {
            if (value == null || value.isBlank() || value.indexOf('/') >= 0) {
                throw new IllegalArgumentException(role + " must be a Java binary name");
            }
            return value;
        }).sorted().toList();
    }
}
