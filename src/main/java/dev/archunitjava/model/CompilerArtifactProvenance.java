package dev.archunitjava.model;

/** Compiler-artifact flags inherited by bytecode evidence from its declaring type and member. */
public record CompilerArtifactProvenance(
        boolean syntheticType, boolean syntheticMember, boolean bridgeMember) {
    public static CompilerArtifactProvenance sourceDeclared() {
        return new CompilerArtifactProvenance(false, false, false);
    }

    public boolean compilerCreated() {
        return syntheticType || syntheticMember || bridgeMember;
    }
}
