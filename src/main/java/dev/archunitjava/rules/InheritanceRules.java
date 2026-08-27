package dev.archunitjava.rules;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.HierarchyQueryResult;
import dev.archunitjava.model.HierarchyRelationship;
import dev.archunitjava.model.HierarchyRelationshipKind;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeKind;
import dev.archunitjava.model.TypeHierarchy;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.SelectorDescription;
import dev.archunitjava.selector.TypeSelection;
import dev.archunitjava.selector.TypeSelector;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Deterministic inheritance, implementation, assignability, and sealed-hierarchy rules. */
public final class InheritanceRules {
    private static final String UNKNOWN_CODE = "rule.hierarchy.unknown";

    private InheritanceRules() {}

    public static ArchitectureRule types(
            TypeModelResult model,
            TypeSelector subjects,
            TypeSelector targets,
            HierarchyRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        return types(model, TypeHierarchy.of(model.types()), subjects, targets, spec);
    }

    public static ArchitectureRule types(
            TypeModelResult model,
            TypeHierarchy hierarchy,
            TypeSelector subjects,
            TypeSelector targets,
            HierarchyRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        TypeHierarchy typeHierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
        TypeSelector subjectSelector = Objects.requireNonNull(subjects, "subjects");
        TypeSelector targetSelector = Objects.requireNonNull(targets, "targets");
        HierarchyRuleSpec ruleSpec = Objects.requireNonNull(spec, "spec");
        TypeSelection selectedSubjects = subjectSelector.selectFrom(model);
        TypeSelection selectedTargets = targetSelector.selectFrom(model);
        Domain domain = new Domain(
                subjectSelector.description(),
                targetSelector.description(),
                selectedSubjects.selected(),
                selectedTargets.selected(),
                model.types(),
                typeHierarchy);
        return rule(domain, ruleSpec);
    }

    private static ArchitectureRule rule(Domain domain, HierarchyRuleSpec spec) {
        String identity = RuleIdentities.semantic(
                "hierarchy",
                domain.subjectDescription.text(),
                domain.targetDescription.text(),
                spec.toString());
        String description = domain.subjectDescription + " " + spec.mode() + " "
                + spec.depth() + " " + spec.relation() + " " + domain.targetDescription;
        return ArchitectureRules.define(identity, description,
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata,
                        options,
                        List.of(
                                new RuleSelection(
                                        "subjects", domain.subjectDescription, domain.subjects.size()),
                                new RuleSelection(
                                        "targets", domain.targetDescription, domain.targets.size())),
                        terminalDiagnostics -> evaluate(
                                metadata, options, domain, spec, terminalDiagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            CheckOptions options,
            Domain domain,
            HierarchyRuleSpec spec,
            List<Diagnostic> terminalDiagnostics) {
        List<Diagnostic> diagnostics = new ArrayList<>(terminalDiagnostics);
        List<Violation> violations = new ArrayList<>();
        boolean incomplete = false;
        for (JavaType subject : domain.subjects) {
            Analysis analysis = analyze(subject, domain, spec);
            boolean unresolved = analysis.matches.isEmpty() && analysis.unknown;
            if (unresolved && spec.unknownHierarchy() == UnknownInheritancePolicy.FAIL) {
                diagnostics.add(unknownDiagnostic(subject, spec, analysis));
                if (!options.allowIncompleteAnalysis()) {
                    incomplete = true;
                    continue;
                }
            }
            if (spec.mode() == HierarchyRuleMode.MUST_MATCH && analysis.matches.isEmpty()) {
                violations.add(missingViolation(metadata, subject, domain, spec));
            } else if (spec.mode() == HierarchyRuleMode.MUST_NOT_MATCH) {
                analysis.matches.stream()
                        .map(match -> relationshipViolation(metadata, subject, match, spec))
                        .forEach(violations::add);
            }
        }
        List<Violation> stable = violations.stream().sorted().toList();
        if (incomplete) return RuleResult.incomplete(metadata, stable, diagnostics);
        return stable.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, stable, diagnostics);
    }

    private static Analysis analyze(
            JavaType subject, Domain domain, HierarchyRuleSpec spec) {
        if (spec.relation() == HierarchyRelation.PERMITS) {
            List<PathMatch> matches = subject.permittedSubclasses().stream()
                    .map(value -> value.binaryName())
                    .filter(domain.targetNames::contains)
                    .map(target -> new PathMatch(
                            target,
                            List.of(subject.binaryName(), target),
                            evidence(List.of(subject.binaryName(), target), domain.declarations),
                            "PERMITTED_SUBCLASS"))
                    .toList();
            return new Analysis(matches, false, "declared PermittedSubclasses attribute");
        }

        List<PathMatch> matches = relationshipPaths(subject, domain, spec);
        if (spec.depth() == HierarchyDepth.DIRECT) {
            return new Analysis(matches, false, "direct hierarchy declaration");
        }
        HierarchyQueryResult hierarchy = domain.hierarchy.transitiveSupertypes(subject.binaryName());
        String details = "missing=" + hierarchy.missingTypes().stream()
                .map(value -> value.binaryName()).toList()
                + ", cycle=" + hierarchy.cycleDetected();
        return new Analysis(matches, !hierarchy.complete(), details);
    }

    private static List<PathMatch> relationshipPaths(
            JavaType subject, Domain domain, HierarchyRuleSpec spec) {
        TreeMap<String, PathMatch> matches = new TreeMap<>();
        if (spec.relation() == HierarchyRelation.ASSIGNABLE_TO
                && domain.targetNames.contains(subject.binaryName())) {
            matches.put(subject.binaryName(), new PathMatch(
                    subject.binaryName(),
                    List.of(subject.binaryName()),
                    evidence(List.of(subject.binaryName()), domain.declarations),
                    "IDENTITY"));
        }
        if (spec.relation() == HierarchyRelation.IMPLEMENTS
                && isInterface(subject.kind())) {
            return List.copyOf(matches.values());
        }

        ArrayDeque<PathState> pending = new ArrayDeque<>();
        pending.add(new PathState(subject.binaryName(), List.of(subject.binaryName()), List.of()));
        Set<String> expanded = new HashSet<>();
        while (!pending.isEmpty()) {
            PathState state = pending.removeFirst();
            if (!expanded.add(state.current)) continue;
            for (HierarchyRelationship edge : domain.hierarchy.directRelationships(state.current)) {
                if (!traversable(edge, spec.relation())) continue;
                String target = edge.target().binaryName();
                List<String> names = append(state.names, target);
                List<HierarchyRelationshipKind> kinds = append(state.kinds, edge.kind());
                if (domain.targetNames.contains(target)
                        && relationshipMatches(subject, target, kinds, domain, spec.relation())) {
                    matches.putIfAbsent(target, new PathMatch(
                            target,
                            names,
                            evidence(names, domain.declarations),
                            kinds.getLast().name()));
                }
                if (spec.depth() == HierarchyDepth.TRANSITIVE && !state.names.contains(target)) {
                    pending.addLast(new PathState(target, names, kinds));
                }
            }
            if (spec.depth() == HierarchyDepth.DIRECT) break;
        }
        return List.copyOf(matches.values());
    }

    private static boolean traversable(
            HierarchyRelationship relationship, HierarchyRelation relation) {
        return relation != HierarchyRelation.EXTENDS
                || relationship.kind() != HierarchyRelationshipKind.IMPLEMENTS_INTERFACE;
    }

    private static boolean relationshipMatches(
            JavaType subject,
            String target,
            List<HierarchyRelationshipKind> path,
            Domain domain,
            HierarchyRelation relation) {
        return switch (relation) {
            case EXTENDS -> true;
            case IMPLEMENTS -> !isInterface(subject.kind())
                    && domain.typeKinds.get(target) != null
                    && isInterface(domain.typeKinds.get(target))
                    && path.contains(HierarchyRelationshipKind.IMPLEMENTS_INTERFACE);
            case ASSIGNABLE_TO -> true;
            case PERMITS -> throw new AssertionError("Permits is analyzed from sealed metadata");
        };
    }

    private static boolean isInterface(JavaTypeKind kind) {
        return kind == JavaTypeKind.INTERFACE || kind == JavaTypeKind.ANNOTATION;
    }

    private static Diagnostic unknownDiagnostic(
            JavaType subject, HierarchyRuleSpec spec, Analysis analysis) {
        return new Diagnostic(
                UNKNOWN_CODE,
                Severity.ERROR,
                Map.of(
                        "details", analysis.details,
                        "relation", spec.relation().name(),
                        "remediation", "Import or provide complete stubs for ancestors, or explicitly choose IGNORE",
                        "subject", subject.binaryName()));
    }

    private static Violation relationshipViolation(
            RuleMetadata metadata,
            JavaType subject,
            PathMatch match,
            HierarchyRuleSpec spec) {
        TypeId origin = TypeId.ofBinaryName(subject.binaryName());
        TypeId target = TypeId.ofBinaryName(match.target);
        String code = "hierarchy.forbidden";
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code,
                        origin.stableKey(), target.stableKey(), String.join("->", match.path))),
                code,
                metadata.severity(),
                List.of(
                        new ViolationSubject("subject", origin),
                        new ViolationSubject("target", target)),
                match.evidence,
                Map.of(
                        "depth", spec.depth().name(),
                        "path", String.join(" -> ", match.path),
                        "relation", spec.relation().name(),
                        "terminalRelationship", match.terminalRelationship));
    }

    private static Violation missingViolation(
            RuleMetadata metadata,
            JavaType subject,
            Domain domain,
            HierarchyRuleSpec spec) {
        TypeId origin = TypeId.ofBinaryName(subject.binaryName());
        String code = "hierarchy.required";
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code, origin.stableKey())),
                code,
                metadata.severity(),
                List.of(new ViolationSubject("subject", origin)),
                evidence(List.of(subject.binaryName()), domain.declarations),
                Map.of(
                        "depth", spec.depth().name(),
                        "relation", spec.relation().name(),
                        "requiredTargetSelector", domain.targetDescription.text()));
    }

    private static List<DependencyEvidence> evidence(
            Collection<String> path, Map<String, DependencyEvidence> declarations) {
        List<DependencyEvidence> result = path.stream()
                .map(declarations::get)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (result.isEmpty()) {
            throw new IllegalStateException("Hierarchy result lacks declaration evidence");
        }
        return result;
    }

    private static <T> List<T> append(List<T> values, T value) {
        List<T> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private record Domain(
            SelectorDescription subjectDescription,
            SelectorDescription targetDescription,
            List<JavaType> subjects,
            List<JavaType> targets,
            Set<String> targetNames,
            Map<String, JavaTypeKind> typeKinds,
            Map<String, DependencyEvidence> declarations,
            TypeHierarchy hierarchy) {
        private Domain(
                SelectorDescription subjectDescription,
                SelectorDescription targetDescription,
                Collection<JavaType> subjects,
                Collection<JavaType> targets,
                Collection<JavaType> allTypes,
                TypeHierarchy hierarchy) {
            this(
                    Objects.requireNonNull(subjectDescription, "subjectDescription"),
                    Objects.requireNonNull(targetDescription, "targetDescription"),
                    subjects.stream().distinct().sorted().toList(),
                    targets.stream().distinct().sorted().toList(),
                    targets.stream().map(JavaType::binaryName)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    allTypes.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                            JavaType::binaryName, JavaType::kind, (first, ignored) -> first)),
                    declarations(allTypes),
                    Objects.requireNonNull(hierarchy, "hierarchy"));
        }

        private static Map<String, DependencyEvidence> declarations(
                Collection<JavaType> types) {
            TreeMap<String, DependencyEvidence> result = new TreeMap<>();
            types.stream().sorted().forEach(type -> result.putIfAbsent(
                    type.binaryName(),
                    DependencyEvidence.at(type.location().resource().locationId())));
            return Map.copyOf(result);
        }
    }

    private record PathState(
            String current,
            List<String> names,
            List<HierarchyRelationshipKind> kinds) {}

    private record PathMatch(
            String target,
            List<String> path,
            List<DependencyEvidence> evidence,
            String terminalRelationship) {}

    private record Analysis(List<PathMatch> matches, boolean unknown, String details) {}
}
