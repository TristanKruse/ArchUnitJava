package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Bounded meta-annotation traversal with explicit gaps, depth limits, and cycles. */
public record MetaAnnotationResult(
        List<JvmReferenceType> annotations,
        List<JvmReferenceType> missingAnnotationTypes,
        boolean depthLimitReached,
        boolean cycleDetected) {
    public MetaAnnotationResult {
        annotations = sorted(annotations, "annotation");
        missingAnnotationTypes = sorted(missingAnnotationTypes, "missingAnnotationType");
    }

    public boolean complete() {
        return missingAnnotationTypes.isEmpty() && !depthLimitReached && !cycleDetected;
    }

    private static List<JvmReferenceType> sorted(List<JvmReferenceType> values, String name) {
        Objects.requireNonNull(values, name + "s");
        TreeSet<String> sorted = new TreeSet<>();
        values.forEach(value -> sorted.add(
                Objects.requireNonNull(value, name).binaryName()));
        return sorted.stream().map(JvmReferenceType::new).toList();
    }
}
