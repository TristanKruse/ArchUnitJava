package dev.archunitjava.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Resolves meta-annotations from imported values without loading annotation classes. */
public final class MetaAnnotationResolver {
    public static final int MAXIMUM_DEPTH = 256;
    private final Map<String, JavaType> types;

    public MetaAnnotationResolver(Collection<JavaType> types) {
        Objects.requireNonNull(types, "types");
        TreeMap<String, JavaType> indexed = new TreeMap<>();
        for (JavaType type : types) {
            JavaType value = Objects.requireNonNull(type, "type");
            if (indexed.put(value.binaryName(), value) != null) {
                throw new IllegalArgumentException("Duplicate type: " + value.binaryName());
            }
        }
        this.types = Map.copyOf(indexed);
    }

    public MetaAnnotationResult resolve(String annotationBinaryName, int maximumDepth) {
        if (maximumDepth < 0 || maximumDepth > MAXIMUM_DEPTH) {
            throw new IllegalArgumentException(
                    "maximumDepth must be between 0 and " + MAXIMUM_DEPTH);
        }
        JavaType start = types.get(annotationBinaryName);
        if (start == null) {
            return new MetaAnnotationResult(
                    List.of(), List.of(new JvmReferenceType(annotationBinaryName)), false, false);
        }
        TreeSet<String> found = new TreeSet<>();
        TreeSet<String> missing = new TreeSet<>();
        TreeSet<String> expandedAtDepth = new TreeSet<>();
        ArrayDeque<Step> pending = new ArrayDeque<>();
        pending.add(new Step(annotationBinaryName, 0, List.of(annotationBinaryName)));
        boolean limit = false;
        boolean cycle = false;
        while (!pending.isEmpty()) {
            Step step = pending.removeFirst();
            JavaType type = types.get(step.binaryName);
            if (type == null) {
                missing.add(step.binaryName);
                continue;
            }
            String expansionKey = step.binaryName + "@" + step.depth;
            if (!expandedAtDepth.add(expansionKey)) continue;
            List<String> direct = declarationAnnotationTypes(type);
            if (!direct.isEmpty() && step.depth >= maximumDepth) {
                limit = true;
                continue;
            }
            for (String next : direct) {
                if (!next.equals(annotationBinaryName)) found.add(next);
                if (step.path.contains(next)) {
                    cycle = true;
                    continue;
                }
                List<String> path = new ArrayList<>(step.path);
                path.add(next);
                pending.addLast(new Step(next, step.depth + 1, List.copyOf(path)));
            }
        }
        return new MetaAnnotationResult(
                found.stream().map(JvmReferenceType::new).toList(),
                missing.stream().map(JvmReferenceType::new).toList(),
                limit,
                cycle);
    }

    private static List<String> declarationAnnotationTypes(JavaType type) {
        return type.annotations().stream()
                .filter(occurrence -> occurrence.site().kind() == AnnotationSiteKind.TYPE_DECLARATION)
                .map(occurrence -> occurrence.annotation().type().binaryName())
                .distinct()
                .sorted()
                .toList();
    }

    private record Step(String binaryName, int depth, List<String> path) {}
}
