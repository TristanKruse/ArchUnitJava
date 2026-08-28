package dev.archunitjava.metrics;

import dev.archunitjava.graph.TypeId;
import java.util.Objects;
import java.util.Optional;

/** One LCOM result with explicit evidence availability and formula population. */
public record CohesionValue(
        TypeId subject,
        LcomVariant variant,
        MetricAvailability availability,
        Optional<MetricAmount> amount,
        int methodCount,
        int fieldCount,
        String explanation) implements Comparable<CohesionValue> {
    public CohesionValue {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(amount, "amount");
        if (methodCount < 0 || fieldCount < 0) {
            throw new IllegalArgumentException("cohesion counts must not be negative");
        }
        if ((availability == MetricAvailability.COMPUTED) != amount.isPresent()) {
            throw new IllegalArgumentException("only computed cohesion values carry an amount");
        }
        amount.ifPresent(value -> {
            if (value.unit() != variant.metric().unit()) {
                throw new IllegalArgumentException("cohesion value has the wrong unit");
            }
        });
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
    }

    public Optional<MetricSample> sample() {
        return amount.map(value -> new MetricSample(subject, variant.metric(), value));
    }

    @Override
    public int compareTo(CohesionValue other) {
        int result = subject.compareTo(other.subject);
        return result != 0 ? result : variant.compareTo(other.variant);
    }
}
