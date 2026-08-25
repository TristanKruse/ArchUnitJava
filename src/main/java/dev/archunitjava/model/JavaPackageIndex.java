package dev.archunitjava.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Deterministic package aggregation over an imported type collection. */
public final class JavaPackageIndex {
    private final List<JavaPackage> packages;
    private final Map<JavaPackageName, JavaPackage> byName;

    private JavaPackageIndex(Collection<JavaType> types) {
        Objects.requireNonNull(types, "types");
        TreeMap<JavaPackageName, Accumulator> accumulators = new TreeMap<>();
        for (JavaType type : types) {
            JavaType value = Objects.requireNonNull(type, "type");
            JavaPackageName name = new JavaPackageName(value.name().packageName());
            accumulators.computeIfAbsent(name, Accumulator::new).add(value);
        }
        TreeMap<JavaPackageName, JavaPackage> result = new TreeMap<>();
        accumulators.forEach((name, value) -> result.put(name, value.build()));
        byName = Map.copyOf(result);
        packages = List.copyOf(result.values());
    }

    public static JavaPackageIndex of(Collection<JavaType> types) {
        return new JavaPackageIndex(types);
    }

    public List<JavaPackage> all() {
        return packages;
    }

    public Optional<JavaPackage> find(JavaPackageName name) {
        return Optional.ofNullable(byName.get(Objects.requireNonNull(name, "name")));
    }

    public Optional<JavaPackage> find(String packageName) {
        return find(new JavaPackageName(packageName));
    }

    private static boolean isPackageInfo(JavaType type) {
        return type.name().simpleName().equals("package-info");
    }

    private static final class Accumulator {
        private final JavaPackageName name;
        private final List<JavaType> types = new ArrayList<>();
        private final List<JavaType> packageInfoTypes = new ArrayList<>();
        private final List<JavaAnnotationOccurrence> annotations = new ArrayList<>();
        private final List<JavaPackageOrigin> origins = new ArrayList<>();

        private Accumulator(JavaPackageName name) {
            this.name = name;
        }

        private void add(JavaType type) {
            origins.add(JavaPackageOrigin.from(type.location().resource()));
            if (isPackageInfo(type)) {
                packageInfoTypes.add(type);
                type.annotations().stream()
                        .filter(value -> value.site().kind() == AnnotationSiteKind.TYPE_DECLARATION)
                        .map(value -> JavaPackage.packageAnnotation(name, value))
                        .forEach(annotations::add);
            } else {
                types.add(type);
            }
        }

        private JavaPackage build() {
            return new JavaPackage(name, types, packageInfoTypes, annotations, origins);
        }
    }
}
