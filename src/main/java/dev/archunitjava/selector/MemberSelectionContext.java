package dev.archunitjava.selector;

import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaPackage;
import dev.archunitjava.model.JavaPackageIndex;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.JavaTypeName;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class MemberSelectionContext {
    private final Map<JavaTypeName, JavaType> types;
    private final JavaPackageIndex packages;
    private final TypeSelectionContext typeContext;

    MemberSelectionContext(Collection<JavaType> universe) {
        List<JavaType> stable = universe.stream().distinct().sorted().toList();
        TreeMap<JavaTypeName, JavaType> indexed = new TreeMap<>();
        for (JavaType type : stable) indexed.putIfAbsent(type.name(), type);
        types = Map.copyOf(indexed);
        packages = JavaPackageIndex.of(stable);
        typeContext = new TypeSelectionContext(stable);
    }

    Optional<JavaType> owner(JavaMember member) {
        return Optional.ofNullable(types.get(member.owner()));
    }

    Optional<JavaPackage> ownerPackage(JavaMember member) {
        JavaType owner = types.get(member.owner());
        return owner == null ? Optional.empty() : packages.find(owner.packageName());
    }

    TypeSelectionContext typeContext() {
        return typeContext;
    }

    Set<SelectionDiagnostic> diagnostics() {
        return typeContext.diagnostics();
    }
}
