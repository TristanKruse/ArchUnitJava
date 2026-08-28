package dev.archunitjava.metrics;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.projection.ProjectionResult;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Metrics over an explicit projection. Multiple edge kinds/evidence collapse to one directed
 * component relationship. A subject filter forms an induced graph: excluded nodes affect neither
 * reported subjects nor incoming/outgoing/reachability inputs.
 */
public final class DependencyMetricAnalyzer {
    public DependencyMetricReport analyze(
            ProjectionResult projection, Collection<ComponentComposition> compositions) {
        ProjectionResult value = Objects.requireNonNull(projection, "projection");
        List<StableId> subjects = value.graph().nodes().stream().map(node -> node.id()).toList();
        return analyze(value, subjects, compositions);
    }

    public DependencyMetricReport analyze(
            ProjectionResult projection,
            Collection<? extends StableId> includedSubjects,
            Collection<ComponentComposition> compositions) {
        ProjectionResult value = Objects.requireNonNull(projection, "projection");
        TreeSet<StableId> known = new TreeSet<>();
        value.graph().nodes().forEach(node -> known.add(node.id()));
        TreeSet<StableId> selected = new TreeSet<>();
        for (StableId subject : Objects.requireNonNull(includedSubjects, "includedSubjects")) {
            StableId id = Objects.requireNonNull(subject, "includedSubject");
            if (!known.contains(id)) {
                throw new IllegalArgumentException("metric subject is absent from projection: "
                        + id.stableKey());
            }
            selected.add(id);
        }
        TreeMap<StableId, ComponentComposition> compositionById = new TreeMap<>();
        for (ComponentComposition composition : Objects.requireNonNull(
                compositions, "compositions")) {
            ComponentComposition item = Objects.requireNonNull(composition, "composition");
            if (compositionById.putIfAbsent(item.component(), item) != null) {
                throw new IllegalArgumentException(
                        "duplicate component composition: " + item.component().stableKey());
            }
        }

        TreeMap<StableId, TreeSet<StableId>> outgoing = new TreeMap<>();
        TreeMap<StableId, TreeSet<StableId>> incoming = new TreeMap<>();
        selected.forEach(subject -> {
            outgoing.put(subject, new TreeSet<>());
            incoming.put(subject, new TreeSet<>());
        });
        for (DependencyEdge edge : value.graph().edges()) {
            if (!selected.contains(edge.origin()) || !selected.contains(edge.target())
                    || edge.origin().equals(edge.target())) continue;
            outgoing.get(edge.origin()).add(edge.target());
            incoming.get(edge.target()).add(edge.origin());
        }

        List<ComponentDependencyMetrics> components = selected.stream().map(subject -> {
            int ca = incoming.get(subject).size();
            int ce = outgoing.get(subject).size();
            double instability = ca + ce == 0 ? 0.0 : (double) ce / (ca + ce);
            ComponentComposition composition = compositionById.getOrDefault(
                    subject, new ComponentComposition(subject, 0, 0));
            double abstractness = composition.abstractness();
            return new ComponentDependencyMetrics(
                    subject, ca, ce, instability, abstractness,
                    Math.abs(abstractness + instability - 1.0));
        }).toList();
        CumulativeDependencyMetrics cumulative = cumulative(selected, outgoing);
        return DependencyMetricReport.of(value.domain(), components, cumulative);
    }

    private static CumulativeDependencyMetrics cumulative(
            Set<StableId> subjects, Map<StableId, ? extends Set<StableId>> outgoing) {
        TreeMap<StableId, Integer> dependsOn = new TreeMap<>();
        long ccd = 0;
        for (StableId subject : subjects) {
            TreeSet<StableId> reached = new TreeSet<>();
            ArrayDeque<StableId> pending = new ArrayDeque<>();
            pending.add(subject);
            while (!pending.isEmpty()) {
                StableId current = pending.removeFirst();
                if (!reached.add(current)) continue;
                outgoing.get(current).forEach(pending::addLast);
            }
            dependsOn.put(subject, reached.size());
            ccd = Math.addExact(ccd, reached.size());
        }
        int count = subjects.size();
        if (count == 0) {
            return new CumulativeDependencyMetrics(0, 0, 0.0, 0.0, 0.0, Map.of());
        }
        double acd = (double) ccd / count;
        double racd = acd / count;
        double nccd = (double) ccd / balancedBinaryTreeCcd(count);
        return new CumulativeDependencyMetrics(count, ccd, acd, racd, nccd, dependsOn);
    }

    static long balancedBinaryTreeCcd(int components) {
        if (components < 0) throw new IllegalArgumentException("components must not be negative");
        long result = 0;
        for (long index = 1; index <= components; index++) {
            result = Math.addExact(result, 64 - Long.numberOfLeadingZeros(index));
        }
        return result;
    }
}
