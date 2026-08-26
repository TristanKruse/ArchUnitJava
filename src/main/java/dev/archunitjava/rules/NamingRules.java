package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.GeneratedCodeClassifier;
import dev.archunitjava.model.GeneratedCodeOptions;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaNestingKind;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.MemberSelection;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.PackageSelection;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.SelectorDescription;
import dev.archunitjava.selector.TypeSelection;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic positive and negative conventions over distinct Java name/location targets. */
public final class NamingRules {
    private NamingRules() {}

    public static ArchitectureRule types(
            TypeModelResult model,
            TypeSelector selector,
            NamingTarget target,
            JavaPattern pattern,
            PatternRuleMode mode) {
        return types(model, selector, target, pattern, mode, NamingRuleOptions.defaults());
    }

    public static ArchitectureRule types(
            TypeModelResult model,
            TypeSelector selector,
            NamingTarget target,
            JavaPattern pattern,
            PatternRuleMode mode,
            NamingRuleOptions options) {
        Objects.requireNonNull(model, "model");
        TypeSelector subjectSelector = Objects.requireNonNull(selector, "selector");
        NamingRuleOptions inclusion = Objects.requireNonNull(options, "options");
        TargetSpec spec = target(target, pattern, mode, SubjectKind.TYPE);
        TypeSelection selection = subjectSelector.selectFrom(model);
        List<Candidate> candidates = selection.selected().stream()
                .filter(type -> included(type, inclusion))
                .map(type -> typeCandidate(type, spec.target))
                .toList();
        return rule(
                "types",
                effectiveDescription(subjectSelector.description(), inclusion),
                candidates,
                spec);
    }

    public static ArchitectureRule members(
            TypeModelResult model,
            MemberSelector selector,
            NamingTarget target,
            JavaPattern pattern,
            PatternRuleMode mode) {
        return members(model, selector, target, pattern, mode, NamingRuleOptions.defaults());
    }

    public static ArchitectureRule members(
            TypeModelResult model,
            MemberSelector selector,
            NamingTarget target,
            JavaPattern pattern,
            PatternRuleMode mode,
            NamingRuleOptions options) {
        Objects.requireNonNull(model, "model");
        MemberSelector subjectSelector = Objects.requireNonNull(selector, "selector");
        NamingRuleOptions inclusion = Objects.requireNonNull(options, "options");
        TargetSpec spec = target(target, pattern, mode, SubjectKind.MEMBER);
        Map<String, JavaType> types = indexedTypes(model.types());
        MemberSelection selection = subjectSelector.selectFrom(model);
        List<Candidate> candidates = selection.selected().stream()
                .filter(member -> {
                    JavaType owner = types.get(member.owner().binaryName());
                    return owner != null
                            && included(owner, inclusion)
                            && (inclusion.includeGeneratedSubjects() || !generated(member));
                })
                .map(member -> memberCandidate(member, types.get(member.owner().binaryName()), spec.target))
                .toList();
        return rule(
                "members",
                effectiveDescription(subjectSelector.description(), inclusion),
                candidates,
                spec);
    }

    public static ArchitectureRule packages(
            TypeModelResult model,
            PackageSelector selector,
            NamingTarget target,
            JavaPattern pattern,
            PatternRuleMode mode) {
        Objects.requireNonNull(model, "model");
        PackageSelector subjectSelector = Objects.requireNonNull(selector, "selector");
        TargetSpec spec = target(target, pattern, mode, SubjectKind.PACKAGE);
        PackageSelection selection = subjectSelector.selectFrom(model);
        List<Candidate> candidates = selection.selected().stream()
                .map(value -> packageCandidate(value, spec.target))
                .toList();
        return rule("packages", subjectSelector.description(), candidates, spec);
    }

    private static ArchitectureRule rule(
            String kind,
            SelectorDescription selector,
            List<Candidate> candidates,
            TargetSpec spec) {
        String identity = RuleIdentities.semantic(
                "naming",
                kind,
                selector.text(),
                spec.target.name(),
                spec.pattern.description().toString(),
                spec.mode.name());
        String description = selector + " " + spec.mode + " " + spec.target
                + " pattern " + spec.pattern.description();
        return ArchitectureRules.define(identity, description, (metadata, options) ->
                RuleTerminal.evaluate(
                        metadata,
                        options,
                        selector,
                        candidates.size(),
                        terminalDiagnostics -> ordinary(
                                metadata, kind, candidates, spec, terminalDiagnostics)));
    }

    private static RuleResult ordinary(
            RuleMetadata metadata,
            String kind,
            List<Candidate> candidates,
            TargetSpec spec,
            List<Diagnostic> diagnostics) {
        List<Violation> violations = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.values.isEmpty()) {
                if (spec.mode == PatternRuleMode.MUST_MATCH) {
                    violations.add(violation(metadata, kind, candidate, spec, "<missing>"));
                }
                continue;
            }
            for (String actual : candidate.values) {
                boolean matches = spec.pattern.matches(actual);
                if ((spec.mode == PatternRuleMode.MUST_MATCH && !matches)
                        || (spec.mode == PatternRuleMode.MUST_NOT_MATCH && matches)) {
                    violations.add(violation(metadata, kind, candidate, spec, actual));
                }
            }
        }
        List<Violation> stable = violations.stream().sorted().toList();
        return stable.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, stable, diagnostics);
    }

    private static Violation violation(
            RuleMetadata metadata,
            String kind,
            Candidate candidate,
            TargetSpec spec,
            String actual) {
        String code = spec.mode == PatternRuleMode.MUST_MATCH
                ? "naming.must-match" : "naming.must-not-match";
        String reportedActual = spec.target == NamingTarget.PACKAGE_NAME && actual.isEmpty()
                ? "<unnamed>" : actual;
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), candidate.id.stableKey(), actual)),
                code,
                metadata.severity(),
                List.of(new ViolationSubject("subject", candidate.id)),
                candidate.evidence,
                Map.of(
                        "actual", reportedActual,
                        "domain", spec.pattern.description().domain().name(),
                        "expression", spec.pattern.description().expression(),
                        "mode", spec.mode.name(),
                        "subjectKind", kind,
                        "syntax", spec.pattern.description().syntax().name(),
                        "target", spec.target.name()));
    }

    private static TargetSpec target(
            NamingTarget target,
            JavaPattern pattern,
            PatternRuleMode mode,
            SubjectKind subjectKind) {
        NamingTarget actualTarget = Objects.requireNonNull(target, "target");
        JavaPattern actualPattern = Objects.requireNonNull(pattern, "pattern");
        PatternRuleMode actualMode = Objects.requireNonNull(mode, "mode");
        if (actualPattern.description().domain() != actualTarget.patternDomain()) {
            throw new IllegalArgumentException(actualTarget + " requires "
                    + actualTarget.patternDomain() + " pattern domain");
        }
        boolean supported = switch (subjectKind) {
            case TYPE -> true;
            case MEMBER -> actualTarget != NamingTarget.BINARY_NAME;
            case PACKAGE -> actualTarget == NamingTarget.PACKAGE_NAME
                    || actualTarget == NamingTarget.ARTIFACT_CONTAINER;
        };
        if (!supported) {
            throw new IllegalArgumentException(actualTarget + " is not available for " + subjectKind);
        }
        return new TargetSpec(actualTarget, actualPattern, actualMode);
    }

    private static Candidate typeCandidate(JavaType type, NamingTarget target) {
        List<String> values = switch (target) {
            case SIMPLE_NAME -> simpleName(type).stream().toList();
            case BINARY_NAME -> List.of(type.binaryName());
            case PACKAGE_NAME -> List.of(type.packageName().value());
            case SOURCE_FILE -> type.location().sourceFile().map(value -> List.of(value.value()))
                    .orElseGet(List::of);
            case CLASS_RESOURCE -> List.of(type.location().resource().entry());
            case ARTIFACT_CONTAINER -> List.of(type.location().resource().container());
        };
        return new Candidate(
                TypeId.ofBinaryName(type.binaryName()),
                values,
                List.of(DependencyEvidence.at(type.location().resource().locationId())));
    }

    private static Candidate memberCandidate(
            JavaMember member, JavaType owner, NamingTarget target) {
        List<String> values = switch (target) {
            case SIMPLE_NAME -> List.of(member.name());
            case BINARY_NAME -> throw new AssertionError();
            case PACKAGE_NAME -> List.of(owner.packageName().value());
            case SOURCE_FILE -> member.location().sourceFile().map(value -> List.of(value.value()))
                    .orElseGet(List::of);
            case CLASS_RESOURCE -> List.of(member.location().resource().entry());
            case ARTIFACT_CONTAINER -> List.of(member.location().resource().container());
        };
        MemberId id = MemberId.of(
                TypeId.ofBinaryName(member.owner().binaryName()),
                member.name(), member.descriptor());
        return new Candidate(
                id,
                values,
                List.of(DependencyEvidence.at(member.location().resource().locationId())));
    }

    private static Candidate packageCandidate(JavaPackage value, NamingTarget target) {
        List<JavaType> types = java.util.stream.Stream.concat(
                        value.types().stream(), value.packageInfoTypes().stream())
                .sorted().toList();
        List<String> values = switch (target) {
            case PACKAGE_NAME -> List.of(value.name().value());
            case ARTIFACT_CONTAINER -> value.origins().stream()
                    .map(origin -> origin.container()).distinct().sorted().toList();
            default -> throw new AssertionError();
        };
        List<DependencyEvidence> evidence = types.stream()
                .map(type -> DependencyEvidence.at(type.location().resource().locationId()))
                .distinct().sorted().toList();
        return new Candidate(
                value.name().isUnnamed()
                        ? PackageId.unnamed() : PackageId.named(value.name().value()),
                values,
                evidence);
    }

    private static boolean included(JavaType type, NamingRuleOptions options) {
        if (!options.includeAnonymousTypes()
                && type.nesting().kind() == JavaNestingKind.ANONYMOUS) return false;
        if (!options.includeLocalTypes()
                && type.nesting().kind() == JavaNestingKind.LOCAL) return false;
        boolean generated = new GeneratedCodeClassifier().classify(
                type,
                GeneratedCodeOptions.enabled(options.generatedAnnotationBinaryNames()))
                .generated();
        return options.includeGeneratedSubjects() || !generated;
    }

    private static boolean generated(JavaMember member) {
        return member.modifiers().contains(JavaMemberModifier.SYNTHETIC)
                || member.modifiers().contains(JavaMemberModifier.BRIDGE);
    }

    private static java.util.Optional<String> simpleName(JavaType type) {
        return switch (type.nesting().kind()) {
            case TOP_LEVEL -> java.util.Optional.of(type.name().simpleName());
            case ANONYMOUS -> java.util.Optional.empty();
            case LOCAL, MEMBER, UNKNOWN -> type.nesting().simpleName();
        };
    }

    private static SelectorDescription effectiveDescription(
            SelectorDescription selector, NamingRuleOptions options) {
        return new SelectorDescription(selector.text()
                + " [anonymous=" + options.includeAnonymousTypes()
                + ", local=" + options.includeLocalTypes()
                + ", generated=" + options.includeGeneratedSubjects()
                + ", generatedAnnotations="
                + String.join(",", options.generatedAnnotationBinaryNames()) + "]");
    }

    private static Map<String, JavaType> indexedTypes(Collection<JavaType> types) {
        TreeMap<String, JavaType> result = new TreeMap<>();
        types.forEach(type -> result.putIfAbsent(type.binaryName(), type));
        return Map.copyOf(result);
    }

    private enum SubjectKind { TYPE, MEMBER, PACKAGE }

    private record TargetSpec(
            NamingTarget target, JavaPattern pattern, PatternRuleMode mode) {}

    private record Candidate(
            StableId id, List<String> values, List<DependencyEvidence> evidence) {
        private Candidate {
            Objects.requireNonNull(id, "id");
            values = values.stream().distinct().sorted().toList();
            evidence = evidence.stream().distinct().sorted().toList();
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException("Naming candidates require declaration evidence");
            }
        }
    }
}
