package dev.archunitjava.rules;

import dev.archunitjava.model.JavaCodeAccessKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable access mood, independently selected opcode kinds, and compiler-artifact policy. */
public record MemberAccessRuleSpec(
        MemberAccessRuleMode mode,
        Set<JavaCodeAccessKind> accessKinds,
        CompilerAccessPolicy compilerAccesses) {
    public MemberAccessRuleSpec {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(accessKinds, "accessKinds");
        if (accessKinds.isEmpty()) {
            throw new IllegalArgumentException("accessKinds must not be empty");
        }
        EnumSet<JavaCodeAccessKind> copy = EnumSet.noneOf(JavaCodeAccessKind.class);
        accessKinds.forEach(value -> copy.add(Objects.requireNonNull(value, "accessKind")));
        accessKinds = Collections.unmodifiableSet(copy);
        Objects.requireNonNull(compilerAccesses, "compilerAccesses");
    }

    public static MemberAccessRuleSpec no(JavaCodeAccessKind... kinds) {
        return strict(MemberAccessRuleMode.NO, kinds);
    }

    public static MemberAccessRuleSpec only(JavaCodeAccessKind... kinds) {
        return strict(MemberAccessRuleMode.ONLY, kinds);
    }

    public static MemberAccessRuleSpec any(JavaCodeAccessKind... kinds) {
        return strict(MemberAccessRuleMode.ANY, kinds);
    }

    public static MemberAccessRuleSpec required(JavaCodeAccessKind... kinds) {
        return strict(MemberAccessRuleMode.REQUIRED, kinds);
    }

    public MemberAccessRuleSpec withCompilerAccesses(CompilerAccessPolicy value) {
        return new MemberAccessRuleSpec(mode, accessKinds, value);
    }

    public String semanticKey() {
        return mode + ":" + accessKinds.stream().map(Enum::name).sorted().toList()
                + ":" + compilerAccesses;
    }

    private static MemberAccessRuleSpec strict(
            MemberAccessRuleMode mode, JavaCodeAccessKind... kinds) {
        Objects.requireNonNull(kinds, "kinds");
        return new MemberAccessRuleSpec(
                mode,
                kinds.length == 0
                        ? Set.of()
                        : EnumSet.copyOf(java.util.Arrays.asList(kinds.clone())),
                CompilerAccessPolicy.IGNORE);
    }
}
