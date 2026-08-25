package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Source- or bytecode-oriented presentation with complete dependency evidence kept separately. */
public record CompilerArtifactView(
        List<JavaType> presentedTypes,
        List<JavaMember> presentedMembers,
        List<JavaParameter> presentedParameters,
        List<JavaCodeAccess> dependencyEvidence) {
    public CompilerArtifactView {
        presentedTypes = sorted(presentedTypes, "presentedType");
        presentedMembers = sorted(presentedMembers, "presentedMember");
        presentedParameters = sorted(presentedParameters, "presentedParameter");
        dependencyEvidence = sorted(dependencyEvidence, "dependencyEvidence");
    }

    private static <T extends Comparable<? super T>> List<T> sorted(List<T> values, String role) {
        Objects.requireNonNull(values, role + "s");
        return values.stream().map(value -> Objects.requireNonNull(value, role)).sorted().toList();
    }
}
