package dev.archunitjava.rules;

import java.util.Objects;

/** Requires-directive mode plus exact transitive/static-phase modifier constraints. */
public record ModuleRequireRuleSpec(
        ModuleRuleMode mode,
        DirectiveFlagPolicy transitive,
        DirectiveFlagPolicy staticPhase) {
    public ModuleRequireRuleSpec {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(transitive, "transitive");
        Objects.requireNonNull(staticPhase, "staticPhase");
    }

    public static ModuleRequireRuleSpec no() {
        return defaults(ModuleRuleMode.NO);
    }

    public static ModuleRequireRuleSpec only() {
        return defaults(ModuleRuleMode.ONLY);
    }

    public static ModuleRequireRuleSpec required() {
        return defaults(ModuleRuleMode.REQUIRED);
    }

    public ModuleRequireRuleSpec withTransitive(DirectiveFlagPolicy policy) {
        return new ModuleRequireRuleSpec(mode, policy, staticPhase);
    }

    public ModuleRequireRuleSpec withStaticPhase(DirectiveFlagPolicy policy) {
        return new ModuleRequireRuleSpec(mode, transitive, policy);
    }

    private static ModuleRequireRuleSpec defaults(ModuleRuleMode mode) {
        return new ModuleRequireRuleSpec(
                mode, DirectiveFlagPolicy.ANY, DirectiveFlagPolicy.ANY);
    }
}
