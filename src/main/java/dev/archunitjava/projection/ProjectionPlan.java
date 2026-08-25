package dev.archunitjava.projection;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.ModuleId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable, pure projection value shared by package, type, member, classpath, and module views.
 * Every application creates a new graph and never mutates the source graph or this plan.
 */
public record ProjectionPlan(
        ProjectionDomain domain,
        List<ProjectionNodeMapping> explicitMappings,
        Set<DependencyKind> includedKinds,
        Map<DependencyKind, DependencyKind> kindRelabeling,
        boolean retainSelfEdges,
        List<ProjectionEdgeSelector> excludedEdges) {
    public ProjectionPlan {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(explicitMappings, "explicitMappings");
        explicitMappings = explicitMappings.stream()
                .map(value -> Objects.requireNonNull(value, "explicitMapping"))
                .distinct()
                .sorted()
                .toList();
        TreeMap<String, StableId> mappedSources = new TreeMap<>();
        for (ProjectionNodeMapping mapping : explicitMappings) {
            StableId previous = mappedSources.putIfAbsent(
                    mapping.source().stableKey(), mapping.target());
            if (previous != null && !previous.equals(mapping.target())) {
                throw new IllegalArgumentException(
                        "Conflicting projection for " + mapping.source().stableKey());
            }
            requireTargetDomain(domain, mapping.target());
        }
        Objects.requireNonNull(includedKinds, "includedKinds");
        EnumSet<DependencyKind> kinds = includedKinds.isEmpty()
                ? EnumSet.noneOf(DependencyKind.class)
                : EnumSet.copyOf(includedKinds);
        includedKinds = Collections.unmodifiableSet(kinds);
        Objects.requireNonNull(kindRelabeling, "kindRelabeling");
        EnumMap<DependencyKind, DependencyKind> relabeling =
                new EnumMap<>(DependencyKind.class);
        kindRelabeling.forEach((source, target) -> relabeling.put(
                Objects.requireNonNull(source, "sourceKind"),
                Objects.requireNonNull(target, "targetKind")));
        kindRelabeling = Collections.unmodifiableMap(relabeling);
        Objects.requireNonNull(excludedEdges, "excludedEdges");
        excludedEdges = excludedEdges.stream()
                .map(value -> Objects.requireNonNull(value, "excludedEdge"))
                .distinct()
                .sorted()
                .toList();
    }

    public static ProjectionPlan packages() {
        return defaults(ProjectionDomain.PACKAGE, List.of());
    }

    public static ProjectionPlan types() {
        return defaults(ProjectionDomain.TYPE, List.of());
    }

    public static ProjectionPlan members() {
        return defaults(ProjectionDomain.MEMBER, List.of());
    }

    public static ProjectionPlan classPath(Map<? extends StableId, LocationId> mappings) {
        return defaults(ProjectionDomain.CLASSPATH, mappings(mappings));
    }

    public static ProjectionPlan modules(Map<? extends StableId, ModuleId> mappings) {
        return defaults(ProjectionDomain.MODULE, mappings(mappings));
    }

    public ProjectionPlan includingOnly(Set<DependencyKind> kinds) {
        return new ProjectionPlan(
                domain, explicitMappings, kinds, kindRelabeling, retainSelfEdges, excludedEdges);
    }

    public ProjectionPlan relabeling(DependencyKind source, DependencyKind target) {
        EnumMap<DependencyKind, DependencyKind> updated = new EnumMap<>(DependencyKind.class);
        updated.putAll(kindRelabeling);
        updated.put(Objects.requireNonNull(source, "source"), Objects.requireNonNull(target, "target"));
        return new ProjectionPlan(
                domain, explicitMappings, includedKinds, updated, retainSelfEdges, excludedEdges);
    }

    public ProjectionPlan withoutSelfEdges() {
        return new ProjectionPlan(
                domain, explicitMappings, includedKinds, kindRelabeling, false, excludedEdges);
    }

    public ProjectionPlan excluding(
            StableId origin, StableId target, DependencyKind kind) {
        java.util.ArrayList<ProjectionEdgeSelector> updated =
                new java.util.ArrayList<>(excludedEdges);
        updated.add(new ProjectionEdgeSelector(origin, target, kind));
        return new ProjectionPlan(
                domain, explicitMappings, includedKinds, kindRelabeling, retainSelfEdges, updated);
    }

    public ProjectionResult apply(DependencyGraph source) {
        Objects.requireNonNull(source, "source");
        TreeMap<String, ProjectionNodeMapping> mappings = new TreeMap<>();
        explicitMappings.forEach(value -> mappings.put(value.source().stableKey(), value));
        DependencyGraph.Builder projected = DependencyGraph.builder();
        for (var node : source.nodes()) {
            map(node.id(), mappings).ifPresent(projected::addNode);
        }
        java.util.ArrayList<DroppedProjectionEdge> dropped = new java.util.ArrayList<>();
        for (DependencyEdge edge : source.edges()) {
            if (!includedKinds.contains(edge.kind())) {
                dropped.add(new DroppedProjectionEdge(edge, ProjectionDropReason.FILTERED_KIND));
                continue;
            }
            if (excludedEdges.stream().anyMatch(selector -> selector.matches(edge))) {
                dropped.add(new DroppedProjectionEdge(edge, ProjectionDropReason.EXPLICITLY_EXCLUDED));
                continue;
            }
            Optional<StableId> origin = map(edge.origin(), mappings);
            Optional<StableId> target = map(edge.target(), mappings);
            if (origin.isEmpty() || target.isEmpty()) {
                dropped.add(new DroppedProjectionEdge(edge, ProjectionDropReason.UNMAPPED_ENDPOINT));
                continue;
            }
            if (!retainSelfEdges && origin.orElseThrow().equals(target.orElseThrow())) {
                dropped.add(new DroppedProjectionEdge(edge, ProjectionDropReason.SELF_EDGE));
                continue;
            }
            DependencyKind kind = kindRelabeling.getOrDefault(edge.kind(), edge.kind());
            edge.evidence().forEach(evidence -> projected.addDependency(
                    origin.orElseThrow(), target.orElseThrow(), kind, evidence));
        }
        return new ProjectionResult(domain, projected.build(), dropped);
    }

    private Optional<StableId> map(
            StableId source, Map<String, ProjectionNodeMapping> mappings) {
        ProjectionNodeMapping explicit = mappings.get(source.stableKey());
        if (explicit != null) return Optional.of(explicit.target());
        return switch (domain) {
            case PACKAGE -> packageId(source).map(value -> value);
            case TYPE -> typeId(source).map(value -> value);
            case MEMBER -> source instanceof MemberId member
                    ? Optional.of(member)
                    : Optional.empty();
            case CLASSPATH -> source instanceof LocationId location
                    ? Optional.of(location)
                    : Optional.empty();
            case MODULE -> source instanceof ModuleId module
                    ? Optional.of(module)
                    : Optional.empty();
        };
    }

    private static Optional<PackageId> packageId(StableId source) {
        if (source instanceof PackageId value) return Optional.of(value);
        TypeId type = source instanceof TypeId value
                ? value
                : source instanceof MemberId member ? member.owner() : null;
        if (type == null) return Optional.empty();
        int separator = type.binaryName().lastIndexOf('.');
        return Optional.of(separator < 0
                ? PackageId.unnamed()
                : PackageId.named(type.binaryName().substring(0, separator)));
    }

    private static Optional<TypeId> typeId(StableId source) {
        if (source instanceof TypeId value) return Optional.of(value);
        return source instanceof MemberId member ? Optional.of(member.owner()) : Optional.empty();
    }

    private static ProjectionPlan defaults(
            ProjectionDomain domain, List<ProjectionNodeMapping> mappings) {
        return new ProjectionPlan(
                domain,
                mappings,
                EnumSet.allOf(DependencyKind.class),
                Map.of(),
                true,
                List.of());
    }

    private static <T extends StableId> List<ProjectionNodeMapping> mappings(
            Map<? extends StableId, T> values) {
        Objects.requireNonNull(values, "mappings");
        return values.entrySet().stream()
                .map(entry -> new ProjectionNodeMapping(
                        Objects.requireNonNull(entry.getKey(), "source"),
                        Objects.requireNonNull(entry.getValue(), "target")))
                .sorted()
                .toList();
    }

    private static void requireTargetDomain(ProjectionDomain domain, StableId target) {
        boolean valid = switch (domain) {
            case PACKAGE -> target instanceof PackageId;
            case TYPE -> target instanceof TypeId;
            case MEMBER -> target instanceof MemberId;
            case CLASSPATH -> target instanceof LocationId;
            case MODULE -> target instanceof ModuleId;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Projection target does not match " + domain + ": " + target.stableKey());
        }
    }
}
