package dev.archunitjava.rules;

import dev.archunitjava.selector.AnnotationQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable annotation relationship, mood, typed values, and traversal-gap policy. */
public record AnnotationRuleSpec(
        AnnotationQuery query,
        AnnotationRuleMode mode,
        List<AnnotationValueCondition> valueConditions,
        UnknownAnnotationPolicy unknownAnnotations) {
    public AnnotationRuleSpec {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(valueConditions, "valueConditions");
        valueConditions = valueConditions.stream()
                .map(value -> Objects.requireNonNull(value, "valueCondition"))
                .distinct()
                .sorted()
                .toList();
        Objects.requireNonNull(unknownAnnotations, "unknownAnnotations");
    }

    public static AnnotationRuleSpec require(AnnotationQuery query) {
        return strict(query, AnnotationRuleMode.REQUIRE);
    }

    public static AnnotationRuleSpec forbid(AnnotationQuery query) {
        return strict(query, AnnotationRuleMode.FORBID);
    }

    public AnnotationRuleSpec withValueCondition(AnnotationValueCondition condition) {
        List<AnnotationValueCondition> values = new ArrayList<>(valueConditions);
        values.add(Objects.requireNonNull(condition, "condition"));
        return new AnnotationRuleSpec(query, mode, values, unknownAnnotations);
    }

    public AnnotationRuleSpec withUnknownAnnotations(UnknownAnnotationPolicy value) {
        return new AnnotationRuleSpec(query, mode, valueConditions, value);
    }

    public String semanticKey() {
        return query.annotationType().binaryName() + ":" + query.mode() + ":"
                + query.visibility().map(Enum::name).orElse("ANY") + ":"
                + query.maximumMetaDepth() + ":" + mode + ":"
                + valueConditions.stream().map(AnnotationValueCondition::stableKey).toList()
                + ":" + unknownAnnotations;
    }

    private static AnnotationRuleSpec strict(
            AnnotationQuery query, AnnotationRuleMode mode) {
        return new AnnotationRuleSpec(
                query, mode, List.of(), UnknownAnnotationPolicy.FAIL);
    }
}
