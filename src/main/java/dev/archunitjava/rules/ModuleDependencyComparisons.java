package dev.archunitjava.rules;

import dev.archunitjava.graph.DependencyEdge;
import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.model.JavaModule;
import dev.archunitjava.model.JavaModuleIdentity;
import dev.archunitjava.model.JavaModuleKind;
import dev.archunitjava.model.TypeModelResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Pure comparison of Module-attribute requires declarations and observed graph edges. */
public final class ModuleDependencyComparisons {
    private ModuleDependencyComparisons() {}

    public static ModuleDependencyComparison compareRequiresToObserved(
            TypeModelResult model,
            DependencyGraph graph,
            Map<? extends StableId, JavaModuleIdentity> moduleMappings,
            NonExplicitModulePolicy nonExplicitPolicy) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(graph, "graph");
        NonExplicitModulePolicy policy = Objects.requireNonNull(
                nonExplicitPolicy, "nonExplicitPolicy");
        TreeMap<String, JavaModuleIdentity> mappings = new TreeMap<>();
        Objects.requireNonNull(moduleMappings, "moduleMappings").forEach((node, module) ->
                mappings.put(
                        Objects.requireNonNull(node, "mapped node").stableKey(),
                        Objects.requireNonNull(module, "mapped module")));
        rejectNonExplicit(policy, mappings.values());

        List<ModuleDependencyObservation> declared = declared(model.modules(), policy);
        ObservationAccumulator accumulator = observed(graph, mappings, policy);
        TreeSet<String> declaredKeys = declared.stream()
                .map(ModuleDependencyObservation::comparisonKey)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        TreeSet<String> observedKeys = accumulator.observations.stream()
                .map(ModuleDependencyObservation::comparisonKey)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        List<ModuleDependencyObservation> observedWithoutRequires = accumulator.observations.stream()
                .filter(value -> !declaredKeys.contains(value.comparisonKey()))
                .toList();
        List<ModuleDependencyObservation> requiresWithoutObserved = declared.stream()
                .filter(value -> !observedKeys.contains(value.comparisonKey()))
                .toList();
        return new ModuleDependencyComparison(
                declared,
                accumulator.observations,
                observedWithoutRequires,
                requiresWithoutObserved,
                accumulator.unmappedEdges);
    }

    private static List<ModuleDependencyObservation> declared(
            Collection<JavaModule> modules, NonExplicitModulePolicy policy) {
        rejectNonExplicit(policy, modules.stream().map(JavaModule::identity).toList());
        List<ModuleDependencyObservation> result = new ArrayList<>();
        modules.stream()
                .filter(module -> module.identity().kind() == JavaModuleKind.EXPLICIT)
                .sorted()
                .forEach(module -> module.requires().forEach(require -> result.add(
                        new ModuleDependencyObservation(
                                ModuleIdentityId.of(module.identity()),
                                require.moduleName(),
                                "MODULE_DESCRIPTOR",
                                Set.of(),
                                List.of(DependencyEvidence.at(
                                        module.location().resource().locationId()))))));
        return result.stream().distinct().sorted().toList();
    }

    private static ObservationAccumulator observed(
            DependencyGraph graph,
            Map<String, JavaModuleIdentity> mappings,
            NonExplicitModulePolicy policy) {
        TreeMap<String, MutableObservation> result = new TreeMap<>();
        int unmapped = 0;
        for (DependencyEdge edge : graph.edges()) {
            Optional<JavaModuleIdentity> origin = mapping(edge.origin(), mappings);
            Optional<JavaModuleIdentity> target = mapping(edge.target(), mappings);
            if (origin.isEmpty() || target.isEmpty()) {
                unmapped++;
                continue;
            }
            if (origin.orElseThrow().equals(target.orElseThrow())) continue;
            if (origin.orElseThrow().kind() != JavaModuleKind.EXPLICIT) {
                if (policy == NonExplicitModulePolicy.REJECT) {
                    throw new IllegalArgumentException(
                            "Observed dependency has non-explicit origin: "
                                    + origin.orElseThrow().stableKey());
                }
                continue;
            }
            Optional<String> targetName = target.orElseThrow().name();
            if (targetName.isEmpty()) {
                if (policy == NonExplicitModulePolicy.REJECT) {
                    throw new IllegalArgumentException("Observed dependency targets unnamed module");
                }
                continue;
            }
            ModuleIdentityId originId = ModuleIdentityId.of(origin.orElseThrow());
            String key = originId.stableKey() + "->" + targetName.orElseThrow();
            result.computeIfAbsent(
                            key,
                            ignored -> new MutableObservation(originId, targetName.orElseThrow()))
                    .add(edge);
        }
        return new ObservationAccumulator(
                result.values().stream().map(MutableObservation::freeze).toList(), unmapped);
    }

    private static Optional<JavaModuleIdentity> mapping(
            StableId node, Map<String, JavaModuleIdentity> mappings) {
        JavaModuleIdentity exact = mappings.get(node.stableKey());
        if (exact != null) return Optional.of(exact);
        return node instanceof MemberId member
                ? Optional.ofNullable(mappings.get(member.owner().stableKey()))
                : Optional.empty();
    }

    private static void rejectNonExplicit(
            NonExplicitModulePolicy policy, Collection<JavaModuleIdentity> modules) {
        if (policy != NonExplicitModulePolicy.REJECT) return;
        modules.stream()
                .filter(module -> module.kind() != JavaModuleKind.EXPLICIT)
                .findFirst()
                .ifPresent(module -> {
                    throw new IllegalArgumentException(
                            "Non-explicit module requires SKIP policy: " + module.stableKey());
                });
    }

    private static final class MutableObservation {
        private final ModuleIdentityId origin;
        private final String target;
        private final EnumSet<DependencyKind> kinds = EnumSet.noneOf(DependencyKind.class);
        private final TreeSet<DependencyEvidence> evidence = new TreeSet<>();

        private MutableObservation(ModuleIdentityId origin, String target) {
            this.origin = origin;
            this.target = target;
        }

        private void add(DependencyEdge edge) {
            kinds.add(edge.kind());
            evidence.addAll(edge.evidence());
        }

        private ModuleDependencyObservation freeze() {
            return new ModuleDependencyObservation(
                    origin, target, "OBSERVED_BYTECODE", kinds, List.copyOf(evidence));
        }
    }

    private record ObservationAccumulator(
            List<ModuleDependencyObservation> observations, int unmappedEdges) {}
}
