package dev.archunitjava.rules;

import dev.archunitjava.pattern.JavaPattern;
import java.util.Objects;
import java.util.Optional;

/** Exports/opens mode with qualification and optional qualified target matching. */
public record ModulePackageRuleSpec(
        ModuleRuleMode mode,
        DirectiveQualification qualification,
        Optional<JavaPattern> targetModule) {
    public ModulePackageRuleSpec {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(qualification, "qualification");
        Objects.requireNonNull(targetModule, "targetModule");
        targetModule.ifPresent(pattern -> {
            if (pattern.description().domain() != dev.archunitjava.pattern.PatternDomain.QUALIFIED_NAME) {
                throw new IllegalArgumentException("Target module requires QUALIFIED_NAME pattern");
            }
        });
        if (targetModule.isPresent() && qualification == DirectiveQualification.UNQUALIFIED) {
            throw new IllegalArgumentException("Unqualified directive cannot have target module pattern");
        }
    }

    public static ModulePackageRuleSpec no() {
        return defaults(ModuleRuleMode.NO);
    }

    public static ModulePackageRuleSpec only() {
        return defaults(ModuleRuleMode.ONLY);
    }

    public static ModulePackageRuleSpec required() {
        return defaults(ModuleRuleMode.REQUIRED);
    }

    public ModulePackageRuleSpec withQualification(DirectiveQualification value) {
        return new ModulePackageRuleSpec(mode, value, targetModule);
    }

    public ModulePackageRuleSpec targetedTo(JavaPattern value) {
        return new ModulePackageRuleSpec(
                mode, DirectiveQualification.QUALIFIED, Optional.of(value));
    }

    private static ModulePackageRuleSpec defaults(ModuleRuleMode mode) {
        return new ModulePackageRuleSpec(mode, DirectiveQualification.ANY, Optional.empty());
    }
}
