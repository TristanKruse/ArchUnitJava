package dev.archunitjava.metrics;

import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.StableId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.JavaModifier;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeKind;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic type grouping for package or explicit module/classpath projections. */
public final class ComponentCompositions {
    private ComponentCompositions() {}

    /** Package-info metadata is excluded; split packages intentionally merge into one package. */
    public static List<ComponentComposition> packages(
            Collection<JavaType> types, AbstractnessScope scope) {
        TreeMap<TypeId, StableId> mappings = new TreeMap<>();
        for (JavaType type : stable(types)) {
            mappings.put(TypeId.ofBinaryName(type.binaryName()), type.packageName().isUnnamed()
                    ? PackageId.unnamed() : PackageId.named(type.packageName().value()));
        }
        return mapped(types, mappings, scope);
    }

    /**
     * Every non-package-info type requires an explicit mapping, allowing two types in one Java
     * package to remain separate in a module projection.
     */
    public static List<ComponentComposition> mapped(
            Collection<JavaType> types,
            Map<TypeId, ? extends StableId> componentByType,
            AbstractnessScope scope) {
        Objects.requireNonNull(componentByType, "componentByType");
        AbstractnessScope policy = Objects.requireNonNull(scope, "scope");
        TreeMap<StableId, MutableComposition> grouped = new TreeMap<>();
        for (JavaType type : stable(types)) {
            if (type.name().simpleName().equals("package-info")) continue;
            TypeId typeId = TypeId.ofBinaryName(type.binaryName());
            StableId component = componentByType.get(typeId);
            if (component == null) {
                throw new IllegalArgumentException("missing component mapping for " + typeId.stableKey());
            }
            MutableComposition composition = grouped.computeIfAbsent(
                    component, ignored -> new MutableComposition());
            if (policy == AbstractnessScope.PUBLIC_TYPES
                    && !type.modifiers().contains(JavaModifier.PUBLIC)) continue;
            composition.types++;
            if (isAbstract(type)) composition.abstractTypes++;
        }
        return grouped.entrySet().stream()
                .map(entry -> new ComponentComposition(
                        entry.getKey(), entry.getValue().types, entry.getValue().abstractTypes))
                .toList();
    }

    private static boolean isAbstract(JavaType type) {
        return type.modifiers().contains(JavaModifier.ABSTRACT)
                || type.kind() == JavaTypeKind.INTERFACE
                || type.kind() == JavaTypeKind.ANNOTATION;
    }

    private static List<JavaType> stable(Collection<JavaType> types) {
        return Objects.requireNonNull(types, "types").stream()
                .map(type -> Objects.requireNonNull(type, "type"))
                .distinct().sorted().toList();
    }

    private static final class MutableComposition {
        private int types;
        private int abstractTypes;
    }
}
