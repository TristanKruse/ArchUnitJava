package dev.archunitjava.presets;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Inspectable recipe that expands to one ordinary {@code ArchitectureRule}. */
public record PresetRule(
        String key,
        PresetRuleMode mode,
        List<String> subjects,
        List<String> relatedLayers,
        Optional<String> displayName,
        Optional<String> rationale)
        implements Comparable<PresetRule> {
    public PresetRule {
        key = text(key, "rule key");
        Objects.requireNonNull(mode, "mode");
        subjects = names(subjects, "subjects");
        relatedLayers = names(relatedLayers, "relatedLayers");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(rationale, "rationale");
        displayName = displayName.map(value -> text(value, "displayName"));
        rationale = rationale.map(value -> text(value, "rationale"));
        validate(mode, subjects, relatedLayers);
    }

    public static PresetRule exactlyOneLayer(String key) {
        return new PresetRule(
                key, PresetRuleMode.EXACTLY_ONE_LAYER,
                List.of(), List.of(), Optional.empty(), Optional.empty());
    }

    public static PresetRule mayOnlyAccess(
            String key, String subject, Collection<String> allowedLayers) {
        return binary(key, PresetRuleMode.MAY_ONLY_ACCESS, subject, allowedLayers);
    }

    public static PresetRule noAccess(
            String key, String subject, Collection<String> forbiddenLayers) {
        return binary(key, PresetRuleMode.NO_ACCESS, subject, forbiddenLayers);
    }

    public static PresetRule onlyAccessedBy(
            String key, String subject, Collection<String> allowedLayers) {
        return binary(key, PresetRuleMode.ONLY_ACCESSED_BY, subject, allowedLayers);
    }

    public static PresetRule isolated(String key, String subject) {
        return new PresetRule(
                key, PresetRuleMode.ISOLATED,
                List.of(subject), List.of(), Optional.empty(), Optional.empty());
    }

    public static PresetRule publicInterface(
            String key,
            Collection<String> consumerLayers,
            Collection<String> approvedInterfaceLayers) {
        return new PresetRule(
                key,
                PresetRuleMode.PUBLIC_INTERFACE,
                List.copyOf(consumerLayers),
                List.copyOf(approvedInterfaceLayers),
                Optional.empty(),
                Optional.empty());
    }

    public PresetRule as(String value) {
        return new PresetRule(
                key, mode, subjects, relatedLayers,
                Optional.of(text(value, "displayName")), rationale);
    }

    public PresetRule because(String value) {
        return new PresetRule(
                key, mode, subjects, relatedLayers,
                displayName, Optional.of(text(value, "rationale")));
    }

    PresetRule renamedLayer(String oldName, String newName) {
        return new PresetRule(
                key,
                mode,
                replace(subjects, oldName, newName),
                replace(relatedLayers, oldName, newName),
                displayName,
                rationale);
    }

    @Override
    public int compareTo(PresetRule other) {
        return key.compareTo(other.key);
    }

    private static PresetRule binary(
            String key,
            PresetRuleMode mode,
            String subject,
            Collection<String> relatedLayers) {
        return new PresetRule(
                key,
                mode,
                List.of(subject),
                List.copyOf(Objects.requireNonNull(relatedLayers, "relatedLayers")),
                Optional.empty(),
                Optional.empty());
    }

    private static void validate(
            PresetRuleMode mode, List<String> subjects, List<String> relatedLayers) {
        switch (mode) {
            case EXACTLY_ONE_LAYER -> {
                if (!subjects.isEmpty() || !relatedLayers.isEmpty()) {
                    throw new IllegalArgumentException("Coverage rules do not name individual layers");
                }
            }
            case ISOLATED -> {
                if (subjects.size() != 1 || !relatedLayers.isEmpty()) {
                    throw new IllegalArgumentException("Isolation requires one subject layer");
                }
            }
            case MAY_ONLY_ACCESS, NO_ACCESS, ONLY_ACCESSED_BY -> {
                if (subjects.size() != 1) {
                    throw new IllegalArgumentException(mode + " requires one subject layer");
                }
            }
            case PUBLIC_INTERFACE -> {
                if (subjects.isEmpty() || relatedLayers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Public-interface rules require consumer and approved layers");
                }
            }
        }
    }

    private static List<String> names(Collection<String> values, String role) {
        Objects.requireNonNull(values, role);
        return values.stream()
                .map(value -> text(value, "layer name"))
                .distinct().sorted().toList();
    }

    private static List<String> replace(List<String> values, String oldName, String newName) {
        return values.stream()
                .map(value -> value.equals(oldName) ? newName : value)
                .distinct().sorted().toList();
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value.trim();
    }
}
