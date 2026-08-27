package dev.archunitjava.rules;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.AnnotationSiteKind;
import dev.archunitjava.model.JavaAnnotationOccurrence;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaParameter;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeUseTarget;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import dev.archunitjava.selector.AnnotationMatchMode;
import dev.archunitjava.selector.AnnotationQuery;
import dev.archunitjava.selector.MemberSelection;
import dev.archunitjava.selector.MemberSelector;
import dev.archunitjava.selector.PackageSelection;
import dev.archunitjava.selector.PackageSelector;
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
import java.util.TreeSet;

/** Annotation rules over declarations, parameters, packages, and exact type-use sites. */
public final class AnnotationRules {
    private static final String UNKNOWN_CODE = "rule.annotation.unknown";
    private static final String JAVA_INHERITED = "java.lang.annotation.Inherited";

    private AnnotationRules() {}

    public static ArchitectureRule types(
            TypeModelResult model, TypeSelector subjects, AnnotationRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        TypeSelector selector = Objects.requireNonNull(subjects, "subjects");
        AnnotationRuleSpec ruleSpec = validate(spec, SubjectKind.TYPE);
        TypeSelection selection = selector.selectFrom(model);
        List<Candidate> candidates = selection.selected().stream()
                .map(AnnotationRules::candidate)
                .toList();
        return rule(selector.description(), candidates, model.types(), ruleSpec, SubjectKind.TYPE);
    }

    public static ArchitectureRule members(
            TypeModelResult model, MemberSelector subjects, AnnotationRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        MemberSelector selector = Objects.requireNonNull(subjects, "subjects");
        AnnotationRuleSpec ruleSpec = validate(spec, SubjectKind.MEMBER);
        MemberSelection selection = selector.selectFrom(model);
        List<Candidate> candidates = selection.selected().stream()
                .map(AnnotationRules::candidate)
                .toList();
        return rule(selector.description(), candidates, model.types(), ruleSpec, SubjectKind.MEMBER);
    }

    public static ArchitectureRule parameters(
            TypeModelResult model, MemberSelector owningCodeUnits, AnnotationRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        MemberSelector selector = Objects.requireNonNull(owningCodeUnits, "owningCodeUnits");
        AnnotationRuleSpec ruleSpec = validate(spec, SubjectKind.PARAMETER);
        MemberSelection selection = selector.selectFrom(model);
        List<Candidate> candidates = selection.selected().stream()
                .filter(JavaMember::isCodeUnit)
                .flatMap(member -> member.parameters().stream()
                        .map(parameter -> candidate(member, parameter)))
                .sorted()
                .toList();
        return rule(
                new SelectorDescription("parameters of " + selector.description()),
                candidates, model.types(), ruleSpec, SubjectKind.PARAMETER);
    }

    public static ArchitectureRule packages(
            TypeModelResult model, PackageSelector subjects, AnnotationRuleSpec spec) {
        Objects.requireNonNull(model, "model");
        PackageSelector selector = Objects.requireNonNull(subjects, "subjects");
        AnnotationRuleSpec ruleSpec = validate(spec, SubjectKind.PACKAGE);
        PackageSelection selection = selector.selectFrom(model);
        List<Candidate> candidates = selection.selected().stream()
                .map(AnnotationRules::candidate)
                .toList();
        return rule(selector.description(), candidates, model.types(), ruleSpec, SubjectKind.PACKAGE);
    }

    private static AnnotationRuleSpec validate(
            AnnotationRuleSpec spec, SubjectKind subjectKind) {
        AnnotationRuleSpec value = Objects.requireNonNull(spec, "spec");
        AnnotationMatchMode mode = value.query().mode();
        if (mode == AnnotationMatchMode.INHERITED_DECLARATION
                && subjectKind != SubjectKind.TYPE) {
            throw new IllegalArgumentException("Only types inherit declaration annotations");
        }
        if (mode == AnnotationMatchMode.TYPE_USE && subjectKind == SubjectKind.PACKAGE) {
            throw new IllegalArgumentException("Packages do not have type-use annotation sites");
        }
        return value;
    }

    private static ArchitectureRule rule(
            SelectorDescription selector,
            List<Candidate> candidates,
            Collection<JavaType> allTypes,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind) {
        Domain domain = new Domain(allTypes);
        String identity = RuleIdentities.semantic(
                "annotation", subjectKind.name(), selector.text(), spec.semanticKey());
        String description = selector + " " + spec.mode() + " "
                + spec.query().mode() + " annotation "
                + spec.query().annotationType().binaryName();
        return ArchitectureRules.define(identity, description,
                (metadata, options) -> RuleTerminal.evaluate(
                        metadata, options, selector, candidates.size(),
                        terminalDiagnostics -> evaluate(
                                metadata, options, candidates, domain,
                                spec, subjectKind, terminalDiagnostics)));
    }

    private static RuleResult evaluate(
            RuleMetadata metadata,
            CheckOptions options,
            List<Candidate> candidates,
            Domain domain,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind,
            List<Diagnostic> terminalDiagnostics) {
        List<Diagnostic> diagnostics = new ArrayList<>(terminalDiagnostics);
        List<Violation> violations = new ArrayList<>();
        boolean incomplete = false;
        for (Candidate candidate : candidates) {
            MatchAnalysis analysis = matches(candidate, domain, spec, subjectKind);
            boolean unresolved = analysis.matches.isEmpty() && analysis.unknown;
            if (unresolved && spec.unknownAnnotations() == UnknownAnnotationPolicy.FAIL) {
                diagnostics.add(unknownDiagnostic(candidate, spec, analysis));
                if (!options.allowIncompleteAnalysis()) {
                    incomplete = true;
                    continue;
                }
            }
            if (spec.mode() == AnnotationRuleMode.REQUIRE && analysis.matches.isEmpty()) {
                violations.add(missingViolation(metadata, candidate, spec, subjectKind));
            } else if (spec.mode() == AnnotationRuleMode.FORBID) {
                analysis.matches.stream()
                        .map(match -> occurrenceViolation(
                                metadata, candidate, match, spec, subjectKind))
                        .forEach(violations::add);
            }
        }
        List<Violation> stable = violations.stream().sorted().toList();
        if (incomplete) return RuleResult.incomplete(metadata, stable, diagnostics);
        return stable.isEmpty()
                ? RuleResult.passed(metadata, diagnostics)
                : RuleResult.failed(metadata, stable, diagnostics);
    }

    private static MatchAnalysis matches(
            Candidate candidate,
            Domain domain,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind) {
        return switch (spec.query().mode()) {
            case DIRECT_DECLARATION -> exact(candidate, spec, false, subjectKind);
            case TYPE_USE -> exact(candidate, spec, true, subjectKind);
            case META_ANNOTATION -> meta(candidate, domain, spec, subjectKind);
            case INHERITED_DECLARATION -> inherited(candidate, domain, spec, subjectKind);
        };
    }

    private static MatchAnalysis exact(
            Candidate candidate,
            AnnotationRuleSpec spec,
            boolean typeUse,
            SubjectKind subjectKind) {
        List<Match> matches = candidate.occurrences.stream()
                .filter(value -> typeUse
                        ? value.site().kind() == AnnotationSiteKind.TYPE_USE
                        : declarationSite(subjectKind, value.site().kind()))
                .filter(value -> annotationMatches(value, spec))
                .map(value -> new Match(
                        value,
                        value,
                        List.of(value.annotation().type().binaryName()),
                        candidate.evidence))
                .toList();
        return new MatchAnalysis(matches, false, "exact class-file annotation sites");
    }

    private static MatchAnalysis meta(
            Candidate candidate,
            Domain domain,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind) {
        List<Match> matches = new ArrayList<>();
        TreeSet<String> unknown = new TreeSet<>();
        for (JavaAnnotationOccurrence carrier : candidate.occurrences) {
            if (!declarationSite(subjectKind, carrier.site().kind())) continue;
            ArrayDeque<MetaStep> pending = new ArrayDeque<>();
            String carrierType = carrier.annotation().type().binaryName();
            pending.add(new MetaStep(carrierType, List.of(carrierType), 0));
            Set<String> expanded = new HashSet<>();
            while (!pending.isEmpty()) {
                MetaStep step = pending.removeFirst();
                JavaType annotationType = domain.types.get(step.type);
                if (annotationType == null) {
                    unknown.add("missing annotation type " + step.type);
                    continue;
                }
                String expansionKey = step.type + "@" + step.depth;
                if (!expanded.add(expansionKey)) continue;
                List<JavaAnnotationOccurrence> declarations = annotationType.annotations().stream()
                        .filter(value -> value.site().kind() == AnnotationSiteKind.TYPE_DECLARATION)
                        .toList();
                if (!declarations.isEmpty() && step.depth >= spec.query().maximumMetaDepth()) {
                    unknown.add("meta depth limit at " + step.type);
                    continue;
                }
                for (JavaAnnotationOccurrence occurrence : declarations) {
                    String next = occurrence.annotation().type().binaryName();
                    List<String> path = append(step.path, next);
                    if (annotationMatches(occurrence, spec)) {
                        matches.add(new Match(
                                carrier,
                                occurrence,
                                path,
                                combinedEvidence(candidate.evidence, path, domain.declarations)));
                    }
                    if (step.path.contains(next)) {
                        unknown.add("meta cycle " + String.join(" -> ", path));
                    } else {
                        pending.addLast(new MetaStep(next, path, step.depth + 1));
                    }
                }
            }
        }
        return new MatchAnalysis(
                matches.stream().distinct().sorted().toList(),
                !unknown.isEmpty(),
                String.join("; ", unknown));
    }

    private static MatchAnalysis inherited(
            Candidate candidate,
            Domain domain,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind) {
        if (subjectKind != SubjectKind.TYPE || candidate.type == null) {
            throw new AssertionError("Inherited matching requires a type candidate");
        }
        JavaType annotationDefinition = domain.types.get(
                spec.query().annotationType().binaryName());
        if (annotationDefinition == null) {
            return new MatchAnalysis(
                    List.of(), true,
                    "missing annotation definition "
                            + spec.query().annotationType().binaryName());
        }
        boolean inherited = annotationDefinition.annotations().stream()
                .filter(value -> value.site().kind() == AnnotationSiteKind.TYPE_DECLARATION)
                .anyMatch(value -> value.annotation().type().binaryName().equals(JAVA_INHERITED));
        if (!inherited) {
            return new MatchAnalysis(
                    List.of(), false, "annotation type is not @Inherited");
        }

        List<String> path = new ArrayList<>();
        path.add(candidate.type.binaryName());
        Set<String> visited = new HashSet<>();
        JavaType current = candidate.type;
        while (current.superclass().isPresent()) {
            String parentName = current.superclass().orElseThrow().binaryName();
            path.add(parentName);
            if (!visited.add(parentName)) {
                return new MatchAnalysis(
                        List.of(), true,
                        "superclass cycle " + String.join(" -> ", path));
            }
            JavaType parent = domain.types.get(parentName);
            if (parent == null) {
                return new MatchAnalysis(
                        List.of(), true, "missing superclass " + parentName);
            }
            List<JavaAnnotationOccurrence> occurrences = parent.annotations().stream()
                    .filter(value -> value.site().kind() == AnnotationSiteKind.TYPE_DECLARATION)
                    .filter(value -> annotationMatches(value, spec))
                    .toList();
            if (!occurrences.isEmpty()) {
                List<String> stablePath = List.copyOf(path);
                return new MatchAnalysis(
                        occurrences.stream().map(value -> new Match(
                                value,
                                value,
                                stablePath,
                                combinedEvidence(
                                        candidate.evidence, stablePath, domain.declarations)))
                                .toList(),
                        false,
                        "inherited superclass declaration");
            }
            current = parent;
        }
        return new MatchAnalysis(List.of(), false, "complete superclass path");
    }

    private static boolean annotationMatches(
            JavaAnnotationOccurrence occurrence, AnnotationRuleSpec spec) {
        AnnotationQuery query = spec.query();
        return occurrence.annotation().type().binaryName()
                        .equals(query.annotationType().binaryName())
                && (query.visibility().isEmpty()
                        || query.visibility().orElseThrow() == occurrence.visibility())
                && spec.valueConditions().stream()
                        .allMatch(condition -> condition.matches(occurrence.annotation()));
    }

    private static boolean declarationSite(
            SubjectKind subjectKind, AnnotationSiteKind siteKind) {
        return switch (subjectKind) {
            case TYPE -> siteKind == AnnotationSiteKind.TYPE_DECLARATION;
            case MEMBER -> siteKind == AnnotationSiteKind.FIELD_DECLARATION
                    || siteKind == AnnotationSiteKind.METHOD_DECLARATION
                    || siteKind == AnnotationSiteKind.CONSTRUCTOR_DECLARATION;
            case PARAMETER -> siteKind == AnnotationSiteKind.PARAMETER;
            case PACKAGE -> siteKind == AnnotationSiteKind.PACKAGE_DECLARATION;
        };
    }

    private static Diagnostic unknownDiagnostic(
            Candidate candidate, AnnotationRuleSpec spec, MatchAnalysis analysis) {
        return new Diagnostic(
                UNKNOWN_CODE,
                Severity.ERROR,
                Map.of(
                        "annotationType", spec.query().annotationType().binaryName(),
                        "details", analysis.details,
                        "matchMode", spec.query().mode().name(),
                        "remediation", "Import the missing hierarchy/annotation types or explicitly choose IGNORE",
                        "subject", candidate.subjectKey));
    }

    private static Violation occurrenceViolation(
            RuleMetadata metadata,
            Candidate candidate,
            Match match,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind) {
        String code = "annotation.forbidden";
        TreeMap<String, String> attributes = attributes(
                candidate, match.placement, match.matched, spec, subjectKind);
        attributes.put("annotationPath", String.join(" -> ", match.annotationPath));
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code, candidate.subjectKey,
                        match.placement.site().stableKey(),
                        match.matched.annotation().stableKey(),
                        String.join("->", match.annotationPath))),
                code,
                metadata.severity(),
                List.of(new ViolationSubject("subject", candidate.id)),
                match.evidence,
                attributes);
    }

    private static Violation missingViolation(
            RuleMetadata metadata,
            Candidate candidate,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind) {
        String code = "annotation.required";
        return new Violation(
                new ViolationId(RuleIdentities.violation(
                        metadata.semanticIdentity(), code, candidate.subjectKey)),
                code,
                metadata.severity(),
                List.of(new ViolationSubject("subject", candidate.id)),
                candidate.evidence,
                Map.of(
                        "annotationType", spec.query().annotationType().binaryName(),
                        "matchMode", spec.query().mode().name(),
                        "subjectKey", candidate.subjectKey,
                        "subjectKind", subjectKind.name(),
                        "valueConditions", spec.valueConditions().stream()
                                .map(AnnotationValueCondition::stableKey).toList().toString(),
                        "visibility", visibility(spec.query())));
    }

    private static TreeMap<String, String> attributes(
            Candidate candidate,
            JavaAnnotationOccurrence placement,
            JavaAnnotationOccurrence matched,
            AnnotationRuleSpec spec,
            SubjectKind subjectKind) {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("annotationType", spec.query().annotationType().binaryName());
        values.put("matchMode", spec.query().mode().name());
        values.put("parameterIndex", placement.site().parameterIndex().isPresent()
                ? Integer.toString(placement.site().parameterIndex().getAsInt()) : "<none>");
        values.put("placementVisibility", placement.visibility().name());
        values.put("siteKind", placement.site().kind().name());
        values.put("siteOwner", placement.site().ownerKey());
        values.put("subjectKey", candidate.subjectKey);
        values.put("subjectKind", subjectKind.name());
        values.put("visibility", matched.visibility().name());
        JavaTypeUseTarget typeUse = placement.site().typeUseTarget().orElse(null);
        values.put("typeUseInfo", typeUse == null ? "<none>" : nonBlank(typeUse.targetInfo()));
        values.put("typeUsePath", typeUse == null
                ? "<none>" : nonBlank(String.join("/", typeUse.path())));
        values.put("typeUseTarget", typeUse == null ? "<none>" : typeUse.targetType());
        values.put("valueConditions", spec.valueConditions().stream()
                .map(AnnotationValueCondition::stableKey).toList().toString());
        return values;
    }

    private static String nonBlank(String value) {
        return value.isBlank() ? "<none>" : value;
    }

    private static String visibility(AnnotationQuery query) {
        return query.visibility().map(Enum::name).orElse("ANY");
    }

    private static Candidate candidate(JavaType type) {
        return new Candidate(
                TypeId.ofBinaryName(type.binaryName()),
                TypeId.ofBinaryName(type.binaryName()).stableKey(),
                type.annotations(),
                List.of(DependencyEvidence.at(type.location().resource().locationId())),
                type);
    }

    private static Candidate candidate(JavaMember member) {
        MemberId id = memberId(member);
        return new Candidate(
                id,
                id.stableKey(),
                member.annotations(),
                List.of(DependencyEvidence.at(member.location().resource().locationId())),
                null);
    }

    private static Candidate candidate(JavaMember member, JavaParameter parameter) {
        MemberId id = memberId(member);
        String parameterInfo = "parameter=" + parameter.index();
        List<JavaAnnotationOccurrence> occurrences = member.annotations().stream()
                .filter(value -> (value.site().kind() == AnnotationSiteKind.PARAMETER
                                && value.site().parameterIndex().orElse(-1) == parameter.index())
                        || (value.site().kind() == AnnotationSiteKind.TYPE_USE
                                && value.site().typeUseTarget().map(target ->
                                        target.targetType().equals("METHOD_FORMAL_PARAMETER")
                                                && target.targetInfo().equals(parameterInfo))
                                        .orElse(false)))
                .toList();
        return new Candidate(
                id,
                id.stableKey() + "@parameter:" + parameter.index(),
                occurrences,
                List.of(DependencyEvidence.at(member.location().resource().locationId())),
                null);
    }

    private static Candidate candidate(JavaPackage value) {
        StableId id = value.name().isUnnamed()
                ? PackageId.unnamed() : PackageId.named(value.name().value());
        List<DependencyEvidence> evidence = java.util.stream.Stream.concat(
                        value.types().stream(), value.packageInfoTypes().stream())
                .map(type -> DependencyEvidence.at(type.location().resource().locationId()))
                .distinct().sorted().toList();
        return new Candidate(id, id.stableKey(), value.annotations(), evidence, null);
    }

    private static MemberId memberId(JavaMember member) {
        return MemberId.of(
                TypeId.ofBinaryName(member.owner().binaryName()),
                member.name(), member.descriptor());
    }

    private static List<DependencyEvidence> combinedEvidence(
            List<DependencyEvidence> subjectEvidence,
            Collection<String> typePath,
            Map<String, DependencyEvidence> declarations) {
        return java.util.stream.Stream.concat(
                        subjectEvidence.stream(),
                        typePath.stream().map(declarations::get).filter(Objects::nonNull))
                .distinct().sorted().toList();
    }

    private static <T> List<T> append(List<T> values, T value) {
        List<T> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private enum SubjectKind { TYPE, MEMBER, PARAMETER, PACKAGE }

    private record Domain(
            Map<String, JavaType> types,
            Map<String, DependencyEvidence> declarations) {
        private Domain(Collection<JavaType> types) {
            this(index(types), declarationEvidence(types));
        }

        private static Map<String, JavaType> index(Collection<JavaType> values) {
            TreeMap<String, JavaType> result = new TreeMap<>();
            values.stream().sorted().forEach(value -> result.putIfAbsent(
                    value.binaryName(), value));
            return Map.copyOf(result);
        }

        private static Map<String, DependencyEvidence> declarationEvidence(
                Collection<JavaType> values) {
            TreeMap<String, DependencyEvidence> result = new TreeMap<>();
            values.stream().sorted().forEach(value -> result.putIfAbsent(
                    value.binaryName(),
                    DependencyEvidence.at(value.location().resource().locationId())));
            return Map.copyOf(result);
        }
    }

    private record Candidate(
            StableId id,
            String subjectKey,
            List<JavaAnnotationOccurrence> occurrences,
            List<DependencyEvidence> evidence,
            JavaType type)
            implements Comparable<Candidate> {
        private Candidate {
            Objects.requireNonNull(id, "id");
            if (subjectKey == null || subjectKey.isBlank()) {
                throw new IllegalArgumentException("subjectKey must not be blank");
            }
            occurrences = occurrences.stream().sorted().toList();
            evidence = evidence.stream().distinct().sorted().toList();
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException("Annotation subjects require declaration evidence");
            }
        }

        @Override
        public int compareTo(Candidate other) {
            return subjectKey.compareTo(other.subjectKey);
        }
    }

    private record MetaStep(String type, List<String> path, int depth) {}

    private record Match(
            JavaAnnotationOccurrence placement,
            JavaAnnotationOccurrence matched,
            List<String> annotationPath,
            List<DependencyEvidence> evidence)
            implements Comparable<Match> {
        @Override
        public int compareTo(Match other) {
            int result = placement.compareTo(other.placement);
            if (result != 0) return result;
            result = matched.compareTo(other.matched);
            if (result != 0) return result;
            return String.join("->", annotationPath)
                    .compareTo(String.join("->", other.annotationPath));
        }
    }

    private record MatchAnalysis(
            List<Match> matches, boolean unknown, String details) {}
}
