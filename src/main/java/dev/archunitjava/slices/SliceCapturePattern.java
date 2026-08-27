package dev.archunitjava.slices;

import dev.archunitjava.model.JavaPackageName;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Safe package template with one {@code {slice}} segment and an optional final {@code ..}. */
public final class SliceCapturePattern implements Comparable<SliceCapturePattern> {
    private static final String CAPTURE = "{slice}";
    private final String template;
    private final List<String> segments;
    private final int captureIndex;
    private final boolean descendants;

    private SliceCapturePattern(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("Slice capture template must not be blank");
        }
        this.template = template;
        descendants = template.endsWith("..");
        String base = descendants ? template.substring(0, template.length() - 2) : template;
        if (base.isBlank() || base.contains("..")) {
            throw new IllegalArgumentException("Invalid slice capture template: " + template);
        }
        segments = List.copyOf(Arrays.asList(base.split("\\.", -1)));
        long captures = segments.stream().filter(CAPTURE::equals).count();
        if (captures != 1 || segments.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "Slice capture template needs exactly one full {slice} segment: " + template);
        }
        captureIndex = segments.indexOf(CAPTURE);
        String validationName = String.join(".", segments).replace(CAPTURE, "captured");
        JavaPackageName.named(validationName);
    }

    public static SliceCapturePattern of(String template) {
        return new SliceCapturePattern(template);
    }

    public String template() {
        return template;
    }

    public Optional<String> capture(JavaPackageName packageName) {
        Objects.requireNonNull(packageName, "packageName");
        if (packageName.isUnnamed()) return Optional.empty();
        List<String> candidate = Arrays.asList(packageName.value().split("\\.", -1));
        if (descendants ? candidate.size() < segments.size() : candidate.size() != segments.size()) {
            return Optional.empty();
        }
        for (int index = 0; index < segments.size(); index++) {
            if (index != captureIndex && !segments.get(index).equals(candidate.get(index))) {
                return Optional.empty();
            }
        }
        return Optional.of(candidate.get(captureIndex));
    }

    @Override
    public int compareTo(SliceCapturePattern other) {
        return template.compareTo(other.template);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SliceCapturePattern pattern && template.equals(pattern.template);
    }

    @Override
    public int hashCode() {
        return template.hashCode();
    }

    @Override
    public String toString() {
        return template;
    }
}
