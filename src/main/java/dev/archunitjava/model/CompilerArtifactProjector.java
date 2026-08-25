package dev.archunitjava.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds presentation views without deleting bytecode evidence from hidden compiler artifacts. */
public final class CompilerArtifactProjector {
    private final GeneratedCodeClassifier generatedClassifier = new GeneratedCodeClassifier();

    public CompilerArtifactView project(
            List<JavaType> types, CompilerArtifactFilterOptions options) {
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(options, "options");
        List<JavaType> presentedTypes = new ArrayList<>();
        List<JavaMember> presentedMembers = new ArrayList<>();
        List<JavaParameter> presentedParameters = new ArrayList<>();
        List<JavaCodeAccess> dependencyEvidence = new ArrayList<>();

        for (JavaType type : types.stream().sorted().toList()) {
            Objects.requireNonNull(type, "type");
            type.declaredMembers().forEach(member -> dependencyEvidence.addAll(member.codeAccesses()));
            boolean hiddenType = !options.includeSyntheticTypes()
                            && type.modifiers().contains(JavaModifier.SYNTHETIC)
                    || !options.includeGeneratedTypes()
                            && generatedClassifier.classify(type, options.generatedCodeOptions()).generated();
            if (hiddenType) continue;
            presentedTypes.add(type);
            for (JavaMember member : type.declaredMembers()) {
                boolean hiddenMember = !options.includeSyntheticMembers()
                                && member.modifiers().contains(JavaMemberModifier.SYNTHETIC)
                        || !options.includeBridgeMembers()
                                && member.modifiers().contains(JavaMemberModifier.BRIDGE);
                if (hiddenMember) continue;
                presentedMembers.add(member);
                member.parameters().stream()
                        .filter(parameter -> options.includeSyntheticParameters()
                                || !parameter.modifiers().contains(JavaParameterModifier.SYNTHETIC))
                        .filter(parameter -> options.includeMandatedParameters()
                                || !parameter.modifiers().contains(JavaParameterModifier.MANDATED))
                        .forEach(presentedParameters::add);
            }
        }
        return new CompilerArtifactView(
                presentedTypes, presentedMembers, presentedParameters, dependencyEvidence);
    }
}
