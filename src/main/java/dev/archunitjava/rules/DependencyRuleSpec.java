package dev.archunitjava.rules;

import java.util.Objects;

/** Immutable dependency mood and edge-boundary policies. */
public record DependencyRuleSpec(
        DependencyRuleMode mode,
        SelfDependencyPolicy selfDependencies,
        ExternalDependencyPolicy externalDependencies) {
    public DependencyRuleSpec {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(selfDependencies, "selfDependencies");
        Objects.requireNonNull(externalDependencies, "externalDependencies");
    }

    public static DependencyRuleSpec noDependencies() {
        return defaults(DependencyRuleMode.NO);
    }

    public static DependencyRuleSpec onlyDependencies() {
        return defaults(DependencyRuleMode.ONLY);
    }

    public static DependencyRuleSpec anyDependency() {
        return defaults(DependencyRuleMode.ANY);
    }

    public static DependencyRuleSpec requiredDependency() {
        return defaults(DependencyRuleMode.REQUIRED);
    }

    public DependencyRuleSpec withSelfDependencies(SelfDependencyPolicy value) {
        return new DependencyRuleSpec(mode, value, externalDependencies);
    }

    public DependencyRuleSpec withExternalDependencies(ExternalDependencyPolicy value) {
        return new DependencyRuleSpec(mode, selfDependencies, value);
    }

    private static DependencyRuleSpec defaults(DependencyRuleMode mode) {
        return new DependencyRuleSpec(
                mode, SelfDependencyPolicy.IGNORE, ExternalDependencyPolicy.FAIL);
    }
}
