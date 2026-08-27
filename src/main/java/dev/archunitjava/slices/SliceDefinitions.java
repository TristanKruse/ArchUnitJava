package dev.archunitjava.slices;

import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builder for capture-based and explicit slice memberships. */
public final class SliceDefinitions {
    private final List<SliceCapturePattern> captures;
    private final List<ExplicitDefinition> explicitDefinitions;
    private final SliceOverlapPolicy overlapPolicy;
    private final UnmatchedSlicePolicy unmatchedPolicy;

    private SliceDefinitions(Builder builder) {
        captures = builder.captures.stream().distinct().sorted().toList();
        TreeMap<String, ExplicitDefinition> stableDefinitions = new TreeMap<>();
        builder.explicitDefinitions.forEach(value ->
                stableDefinitions.putIfAbsent(value.key(), value));
        explicitDefinitions = List.copyOf(stableDefinitions.values());
        overlapPolicy = builder.overlapPolicy;
        unmatchedPolicy = builder.unmatchedPolicy;
        if (captures.isEmpty() && explicitDefinitions.isEmpty()) {
            throw new IllegalArgumentException("At least one slice definition is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public SliceModel build(TypeModelResult model) {
        Objects.requireNonNull(model, "model");
        TreeMap<TypeId, TreeSet<SliceId>> candidates = new TreeMap<>();
        model.types().forEach(type -> candidates.put(typeId(type), new TreeSet<>()));
        for (SliceCapturePattern capture : captures) {
            for (JavaType type : model.types()) {
                capture.capture(type.packageName()).ifPresent(name ->
                        candidates.get(typeId(type)).add(SliceId.named(name)));
            }
        }
        for (ExplicitDefinition definition : explicitDefinitions) {
            definition.selector.selectFrom(model).selected().forEach(type ->
                    candidates.get(typeId(type)).add(definition.id));
        }

        TreeMap<TypeId, SliceId> memberships = new TreeMap<>();
        List<TypeId> unmatched = new ArrayList<>();
        candidates.forEach((type, offered) -> {
            if (offered.isEmpty()) {
                unmatched.add(type);
            } else if (offered.size() == 1 || overlapPolicy == SliceOverlapPolicy.FIRST_BY_NAME) {
                memberships.put(type, offered.getFirst());
            } else {
                throw new SliceMembershipException(
                        "Type " + type.stableKey() + " matches multiple slices " + offered);
            }
        });
        if (!unmatched.isEmpty() && unmatchedPolicy == UnmatchedSlicePolicy.FAIL) {
            throw new SliceMembershipException("Unmatched types: " + unmatched);
        }
        TreeMap<SliceId, List<TypeId>> typesBySlice = new TreeMap<>();
        memberships.forEach((type, slice) ->
                typesBySlice.computeIfAbsent(slice, ignored -> new ArrayList<>()).add(type));
        List<JavaSlice> slices = typesBySlice.entrySet().stream()
                .map(entry -> new JavaSlice(entry.getKey(), entry.getValue()))
                .toList();
        return new SliceModel(slices, memberships, unmatched, definitionKey());
    }

    private String definitionKey() {
        return "captures=" + captures.stream().map(SliceCapturePattern::template).toList()
                + ";explicit=" + explicitDefinitions.stream().map(ExplicitDefinition::key).toList()
                + ";overlap=" + overlapPolicy
                + ";unmatched=" + unmatchedPolicy;
    }

    public static final class Builder {
        private final List<SliceCapturePattern> captures = new ArrayList<>();
        private final List<ExplicitDefinition> explicitDefinitions = new ArrayList<>();
        private SliceOverlapPolicy overlapPolicy = SliceOverlapPolicy.FAIL;
        private UnmatchedSlicePolicy unmatchedPolicy = UnmatchedSlicePolicy.IGNORE;

        public Builder capturePackages(SliceCapturePattern pattern) {
            captures.add(Objects.requireNonNull(pattern, "pattern"));
            return this;
        }

        public Builder assign(String sliceName, TypeSelector selector) {
            explicitDefinitions.add(new ExplicitDefinition(
                    SliceId.named(sliceName), Objects.requireNonNull(selector, "selector")));
            return this;
        }

        public Builder overlapPolicy(SliceOverlapPolicy policy) {
            overlapPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder unmatchedPolicy(UnmatchedSlicePolicy policy) {
            unmatchedPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public SliceDefinitions create() {
            return new SliceDefinitions(this);
        }

        public SliceModel build(TypeModelResult model) {
            return create().build(model);
        }
    }

    private record ExplicitDefinition(SliceId id, TypeSelector selector)
            implements Comparable<ExplicitDefinition> {
        private ExplicitDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(selector, "selector");
        }

        private String key() {
            return id.stableKey() + "=" + selector.description().text();
        }

        @Override
        public int compareTo(ExplicitDefinition other) {
            return key().compareTo(other.key());
        }
    }

    private static TypeId typeId(JavaType type) {
        return TypeId.ofBinaryName(type.binaryName());
    }
}
