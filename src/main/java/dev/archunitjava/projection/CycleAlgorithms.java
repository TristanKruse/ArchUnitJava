package dev.archunitjava.projection;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.StableId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic, graph-generic SCC and bounded elementary-cycle algorithms. */
public final class CycleAlgorithms {
    private CycleAlgorithms() {}

    public static CycleAnalysisResult analyze(DependencyGraph graph) {
        return analyze(graph, CycleEnumerationOptions.defaults());
    }

    public static List<StronglyConnectedComponent> stronglyConnectedComponents(
            DependencyGraph graph) {
        Adjacency adjacency = Adjacency.of(graph);
        return new Tarjan(adjacency).components();
    }

    public static CycleAnalysisResult analyze(
            DependencyGraph graph, CycleEnumerationOptions options) {
        Objects.requireNonNull(options, "options");
        Adjacency adjacency = Adjacency.of(graph);
        List<StronglyConnectedComponent> components = new Tarjan(adjacency).components();
        if (!options.enabled()) {
            return new CycleAnalysisResult(components, List.of(), false, false, 0);
        }
        Enumerator enumerator = new Enumerator(adjacency, options);
        components.stream()
                .filter(StronglyConnectedComponent::cyclic)
                .forEach(enumerator::enumerate);
        return new CycleAnalysisResult(
                components,
                enumerator.cycles,
                true,
                enumerator.truncated,
                enumerator.traversedEdges);
    }

    private record Adjacency(Map<StableId, NavigableSet<StableId>> targets) {
        static Adjacency of(DependencyGraph graph) {
            Objects.requireNonNull(graph, "graph");
            TreeMap<StableId, NavigableSet<StableId>> result = new TreeMap<>();
            graph.nodes().forEach(node -> result.put(node.id(), new TreeSet<>()));
            for (DependencyEdge edge : graph.edges()) {
                result.get(edge.origin()).add(edge.target());
            }
            TreeMap<StableId, NavigableSet<StableId>> immutable = new TreeMap<>();
            result.forEach((node, targets) -> immutable.put(node,
                    java.util.Collections.unmodifiableNavigableSet(new TreeSet<>(targets))));
            return new Adjacency(java.util.Collections.unmodifiableMap(immutable));
        }

        boolean hasSelfLoop(StableId node) {
            return targets.get(node).contains(node);
        }
    }

    private static final class Tarjan {
        private final Adjacency adjacency;
        private final Map<StableId, Integer> indexes = new HashMap<>();
        private final Map<StableId, Integer> lowLinks = new HashMap<>();
        private final Deque<StableId> stack = new ArrayDeque<>();
        private final Set<StableId> onStack = new HashSet<>();
        private final List<StronglyConnectedComponent> components = new ArrayList<>();
        private int nextIndex;

        private Tarjan(Adjacency adjacency) {
            this.adjacency = adjacency;
        }

        List<StronglyConnectedComponent> components() {
            adjacency.targets.keySet().forEach(node -> {
                if (!indexes.containsKey(node)) visit(node);
            });
            return components.stream().sorted().toList();
        }

        private void visit(StableId node) {
            int index = nextIndex++;
            indexes.put(node, index);
            lowLinks.put(node, index);
            stack.push(node);
            onStack.add(node);
            for (StableId target : adjacency.targets.get(node)) {
                if (!indexes.containsKey(target)) {
                    visit(target);
                    lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(target)));
                } else if (onStack.contains(target)) {
                    lowLinks.put(node, Math.min(lowLinks.get(node), indexes.get(target)));
                }
            }
            if (!lowLinks.get(node).equals(indexes.get(node))) return;
            ArrayList<StableId> nodes = new ArrayList<>();
            StableId current;
            do {
                current = stack.pop();
                onStack.remove(current);
                nodes.add(current);
            } while (!current.equals(node));
            boolean cyclic = nodes.size() > 1 || adjacency.hasSelfLoop(nodes.getFirst());
            components.add(new StronglyConnectedComponent(nodes, cyclic));
        }
    }

    private static final class Enumerator {
        private final Adjacency adjacency;
        private final CycleEnumerationOptions options;
        private final List<ElementaryCycle> cycles = new ArrayList<>();
        private long traversedEdges;
        private boolean truncated;
        private boolean stopped;

        private Enumerator(Adjacency adjacency, CycleEnumerationOptions options) {
            this.adjacency = adjacency;
            this.options = options;
        }

        void enumerate(StronglyConnectedComponent component) {
            if (stopped) return;
            Set<StableId> componentNodes = Set.copyOf(component.nodes());
            for (StableId start : component.nodes()) {
                if (stopped) return;
                ArrayList<StableId> path = new ArrayList<>();
                path.add(start);
                HashSet<StableId> visited = new HashSet<>();
                visited.add(start);
                search(start, start, componentNodes, path, visited);
            }
        }

        private void search(
                StableId start,
                StableId current,
                Set<StableId> component,
                ArrayList<StableId> path,
                Set<StableId> visited) {
            for (StableId target : adjacency.targets.get(current)) {
                if (stopped || !component.contains(target) || target.compareTo(start) < 0) continue;
                if (traversedEdges == options.maximumTraversedEdges()) {
                    truncated = true;
                    stopped = true;
                    return;
                }
                traversedEdges++;
                if (target.equals(start)) {
                    if (path.size() <= options.maximumCycleLength()) add(path);
                } else if (!visited.contains(target)) {
                    if (path.size() >= options.maximumCycleLength()) {
                        truncated = true;
                        continue;
                    }
                    visited.add(target);
                    path.add(target);
                    search(start, target, component, path, visited);
                    path.removeLast();
                    visited.remove(target);
                }
            }
        }

        private void add(List<StableId> path) {
            ElementaryCycle cycle = new ElementaryCycle(path);
            if (cycles.contains(cycle)) return;
            if (cycles.size() == options.maximumCycles()) {
                truncated = true;
                stopped = true;
                return;
            }
            cycles.add(cycle);
        }
    }
}
