package dev.archunitjava.rules;

import dev.archunitjava.model.JavaAnnotation;
import dev.archunitjava.model.JavaAnnotationValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One typed condition over an explicitly encoded annotation element. */
public record AnnotationValueCondition(
        String elementName,
        AnnotationValueOperator operator,
        Optional<JavaAnnotationValue> expectedValue)
        implements Comparable<AnnotationValueCondition> {
    public AnnotationValueCondition {
        if (elementName == null || elementName.isBlank()) {
            throw new IllegalArgumentException("elementName must not be blank");
        }
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(expectedValue, "expectedValue");
        boolean expectsValue = operator == AnnotationValueOperator.EQUALS
                || operator == AnnotationValueOperator.NOT_EQUALS;
        if (expectsValue != expectedValue.isPresent()) {
            throw new IllegalArgumentException(operator + " value presence is invalid");
        }
    }

    public static AnnotationValueCondition present(String elementName) {
        return new AnnotationValueCondition(
                elementName, AnnotationValueOperator.PRESENT, Optional.empty());
    }

    public static AnnotationValueCondition absent(String elementName) {
        return new AnnotationValueCondition(
                elementName, AnnotationValueOperator.ABSENT, Optional.empty());
    }

    public static AnnotationValueCondition equalTo(
            String elementName, JavaAnnotationValue value) {
        return new AnnotationValueCondition(
                elementName, AnnotationValueOperator.EQUALS,
                Optional.of(Objects.requireNonNull(value, "value")));
    }

    public static AnnotationValueCondition notEqualTo(
            String elementName, JavaAnnotationValue value) {
        return new AnnotationValueCondition(
                elementName, AnnotationValueOperator.NOT_EQUALS,
                Optional.of(Objects.requireNonNull(value, "value")));
    }

    public boolean matches(JavaAnnotation annotation) {
        Objects.requireNonNull(annotation, "annotation");
        List<JavaAnnotationValue> values = annotation.elements().stream()
                .filter(element -> element.name().equals(elementName))
                .map(element -> element.value())
                .toList();
        return switch (operator) {
            case PRESENT -> !values.isEmpty();
            case ABSENT -> values.isEmpty();
            case EQUALS -> values.stream().anyMatch(expectedValue.orElseThrow()::equals);
            case NOT_EQUALS -> !values.isEmpty()
                    && values.stream().noneMatch(expectedValue.orElseThrow()::equals);
        };
    }

    public String stableKey() {
        return elementName + ":" + operator + ":"
                + expectedValue.map(JavaAnnotationValue::stableKey).orElse("");
    }

    @Override
    public int compareTo(AnnotationValueCondition other) {
        return stableKey().compareTo(other.stableKey());
    }
}
