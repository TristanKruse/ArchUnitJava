package dev.archunitjava.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Opt-in classifier that deliberately cannot classify from a lone annotation. */
public final class GeneratedCodeClassifier {
    public GeneratedCodeClassification classify(JavaType type, GeneratedCodeOptions options) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(options, "options");
        if (!options.enabled()) return new GeneratedCodeClassification(false, List.of());

        List<GeneratedCodeSignal> signals = new ArrayList<>();
        if (type.modifiers().contains(JavaModifier.SYNTHETIC)
                || type.declaredMembers().stream()
                        .anyMatch(member -> member.modifiers().contains(JavaMemberModifier.SYNTHETIC))) {
            signals.add(GeneratedCodeSignal.SYNTHETIC_FLAG);
        }
        String lowerName = type.binaryName().toLowerCase(Locale.ROOT);
        if (lowerName.contains(".generated.")
                || lowerName.contains("$$")
                || lowerName.endsWith("generated")) {
            signals.add(GeneratedCodeSignal.GENERATED_NAME);
        }
        type.location().sourceFile()
                .map(SourceFileName::value)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> value.endsWith("generated.java") || value.endsWith("_generated.java"))
                .ifPresent(ignored -> signals.add(GeneratedCodeSignal.GENERATED_SOURCE_FILE));
        if (type.annotations().stream().map(value -> value.annotation().type().binaryName())
                .anyMatch(options.annotationBinaryNames()::contains)) {
            signals.add(GeneratedCodeSignal.CONFIGURED_ANNOTATION);
        }
        List<GeneratedCodeSignal> distinct = signals.stream().distinct().sorted().toList();
        return new GeneratedCodeClassification(distinct.size() >= 2, distinct);
    }
}
