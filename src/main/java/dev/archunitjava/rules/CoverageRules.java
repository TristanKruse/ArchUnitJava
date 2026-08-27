package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.ModuleSelector;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.SelectorDescription;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Exhaustive, exactly-one assignment checks over imported types, packages, or modules. */
public final class CoverageRules {
    private CoverageRules() {}

    public static ArchitectureRule types(
            TypeModelResult model,
            TypeSelector subjects,
            Collection<TypeCoveragePolicy> policies) {
        return types(model, subjects, TypeSelector.none(), policies);
    }

    public static ArchitectureRule types(
            TypeModelResult model,
            TypeSelector subjects,
            TypeSelector exclusions,
            Collection<TypeCoveragePolicy> policies) {
        Objects.requireNonNull(model, "model");
        TypeSelector subjectSelector = Objects.requireNonNull(subjects, "subjects");
        TypeSelector exclusionSelector = Objects.requireNonNull(exclusions, "exclusions");
        List<TypeCoveragePolicy> definitions = typePolicies(policies);
        Set<TypeId> excluded = exclusionSelector.selectFrom(model).selected().stream()
                .map(type -> TypeId.ofBinaryName(type.binaryName()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Candidate> candidates = subjectSelector.selectFrom(model).selected().stream()
                .map(CoverageRules::typeCandidate)
                .filter(candidate -> !excluded.contains(candidate.id()))
                .toList();
        List<Assignment> assignments = definitions.stream()
                .map(policy -> new Assignment(
                        policy.name(),
                        policy.selector().description().text(),
                        policy.selector().selectFrom(model).selected().stream()
                                .map(type -> (StableId) TypeId.ofBinaryName(type.binaryName()))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())))
                .toList();
        return rule("types", effective(subjectSelector.description(), exclusionSelector.description()),
                candidates, assignments);
    }

    public static ArchitectureRule packages(
            TypeModelResult model,
            PackageSelector subjects,
            Collection<PackageCoveragePolicy> policies) {
        return packages(model, subjects, PackageSelector.none(), policies);
    }

    public static ArchitectureRule packages(
            TypeModelResult model,
            PackageSelector subjects,
            PackageSelector exclusions,
            Collection<PackageCoveragePolicy> policies) {
        Objects.requireNonNull(model, "model");
        PackageSelector subjectSelector = Objects.requireNonNull(subjects, "subjects");
        PackageSelector exclusionSelector = Objects.requireNonNull(exclusions, "exclusions");
        List<PackageCoveragePolicy> definitions = packagePolicies(policies);
        Set<PackageId> excluded = exclusionSelector.selectFrom(model).selected().stream()
                .map(CoverageRules::packageId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Candidate> candidates = subjectSelector.selectFrom(model).selected().stream()
                .map(CoverageRules::packageCandidate)
                .filter(candidate -> !excluded.contains(candidate.id()))
                .toList();
        List<Assignment> assignments = definitions.stream()
                .map(policy -> new Assignment(
                        policy.name(),
                        policy.selector().description().text(),
                        policy.selector().selectFrom(model).selected().stream()
                                .map(value -> (StableId) packageId(value))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())))
                .toList();
        return rule("packages", effective(subjectSelector.description(), exclusionSelector.description()),
                candidates, assignments);
    }

    public static ArchitectureRule modules(
            TypeModelResult model,
            ModuleSelector subjects,
            Collection<ModuleCoveragePolicy> policies) {
        return modules(model, subjects, ModuleSelector.none(), policies);
    }

    public static ArchitectureRule modules(
            TypeModelResult model,
            ModuleSelector subjects,
            ModuleSelector exclusions,
            Collection<ModuleCoveragePolicy> policies) {
        Objects.requireNonNull(model, "model");
        ModuleSelector subjectSelector = Objects.requireNonNull(subjects, "subjects");
        ModuleSelector exclusionSelector = Objects.requireNonNull(exclusions, "exclusions");
        List<ModuleCoveragePolicy> definitions = modulePolicies(policies);
        Set<ModuleIdentityId> excluded = exclusionSelector.selectFrom(model).selected().stream()
                .map(module -> ModuleIdentityId.of(module.identity()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Candidate> candidates = subjectSelector.selectFrom(model).selected().stream()
                .map(CoverageRules::moduleCandidate)
                .filter(candidate -> !excluded.contains(candidate.id()))
                .toList();
        List<Assignment> assignments = definitions.stream()
                .map(policy -> new Assignment(
                        policy.name(),
                        policy.selector().description().text(),
                        policy.selector().selectFrom(model).selected().stream()
                                .map(module -> (StableId) ModuleIdentityId.of(module.identity()))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet())))
                .toList();
        return rule(
                "modules",
                effective(subjectSelector.description(), exclusionSelector.description()),
                candidates,
                assignments);
    }

    private static ArchitectureRule rule(
            String subjectKind,
            SelectorDescription selector,
            List<Candidate> candidates,
            List<Assignment> assignments) {
        String assignmentIdentity = assignments.stream()
                .map(value -> value.name() + "=" + value.selectorDescription())
                .toList().toString();
        String identity = RuleIdentities.semantic(
                "coverage", subjectKind, selector.text(), assignmentIdentity);
        String description = selector + " must belong to exactly one named " + subjectKind + " policy";
        return ArchitectureRules.define(identity, description, (metadata, options) ->
                RuleTerminal.evaluate(metadata, options, selector, candidates.size(), diagnostics ->
                        ordinary(metadata, subjectKind, candidates, assignments, diagnostics)));
    }

    private static RuleResult ordinary(
            RuleMetadata metadata,
            String subjectKind,
            List<Candidate> candidates,
            List<Assignment> assignments,
            List<Diagnostic> diagnostics) {
        List<Violation> violations = new ArrayList<>();
        for (Candidate candidate : candidates) {
            List<String> matched = assignments.stream()
                    .filter(value -> value.members().contains(candidate.id()))
                    .map(Assignment::name)
                    .sorted()
                    .toList();
            if (matched.size() != 1) {
                violations.add(violation(metadata, subjectKind, candidate, matched));
            }
        }
        List<Violation> stable = violations.stream().sorted().toList();
        return stable.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, stable, diagnostics);
    }

    private static Violation violation(
            RuleMetadata metadata,
            String subjectKind,
            Candidate candidate,
            List<String> assignments) {
        String code = assignments.isEmpty()
                ? "coverage.unassigned" : "coverage.multiply-assigned";
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code, candidate.id().stableKey(), assignments.toString())),
                code,
                metadata.severity(),
                List.of(new ViolationSubject("subject", candidate.id())),
                candidate.evidence(),
                Map.of(
                        "assignmentCount", Integer.toString(assignments.size()),
                        "assignments", assignments.toString(),
                        "subjectKind", subjectKind));
    }

    private static Candidate typeCandidate(JavaType type) {
        return new Candidate(
                TypeId.ofBinaryName(type.binaryName()),
                List.of(DependencyEvidence.at(type.location().resource().locationId())));
    }

    private static Candidate packageCandidate(JavaPackage value) {
        List<DependencyEvidence> evidence = java.util.stream.Stream.concat(
                        value.types().stream(), value.packageInfoTypes().stream())
                .map(type -> DependencyEvidence.at(type.location().resource().locationId()))
                .distinct().sorted().toList();
        return new Candidate(packageId(value), evidence);
    }

    private static Candidate moduleCandidate(JavaModule module) {
        return new Candidate(
                ModuleIdentityId.of(module.identity()),
                List.of(DependencyEvidence.at(module.location().resource().locationId())));
    }

    private static PackageId packageId(JavaPackage value) {
        return value.name().isUnnamed()
                ? PackageId.unnamed() : PackageId.named(value.name().value());
    }

    private static SelectorDescription effective(
            SelectorDescription subjects, SelectorDescription exclusions) {
        return new SelectorDescription(subjects.text() + " excluding " + exclusions.text());
    }

    private static List<TypeCoveragePolicy> typePolicies(Collection<TypeCoveragePolicy> values) {
        return policies(values, TypeCoveragePolicy::name, "type");
    }

    private static List<PackageCoveragePolicy> packagePolicies(Collection<PackageCoveragePolicy> values) {
        return policies(values, PackageCoveragePolicy::name, "package");
    }

    private static List<ModuleCoveragePolicy> modulePolicies(Collection<ModuleCoveragePolicy> values) {
        return policies(values, ModuleCoveragePolicy::name, "module");
    }

    private static <T> List<T> policies(
            Collection<T> values,
            java.util.function.Function<T, String> name,
            String subjectKind) {
        Objects.requireNonNull(values, "policies");
        List<T> result = values.stream()
                .map(value -> Objects.requireNonNull(value, "policy"))
                .sorted(java.util.Comparator.comparing(name))
                .toList();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(subjectKind + " coverage requires at least one policy");
        }
        Set<String> seen = new HashSet<>();
        for (T value : result) {
            if (!seen.add(name.apply(value))) {
                throw new IllegalArgumentException("Duplicate coverage policy name: " + name.apply(value));
            }
        }
        return result;
    }

    private record Candidate(StableId id, List<DependencyEvidence> evidence) {
        private Candidate {
            Objects.requireNonNull(id, "id");
            evidence = evidence.stream().distinct().sorted().toList();
        }
    }

    private record Assignment(String name, String selectorDescription, Set<StableId> members) {
        private Assignment {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(selectorDescription, "selectorDescription");
            members = Set.copyOf(members);
        }
    }
}
