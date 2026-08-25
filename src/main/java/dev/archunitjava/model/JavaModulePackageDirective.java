package dev.archunitjava.model;

import dev.archunitjava.graph.ModuleId;
import java.util.List;
import java.util.Objects;

/** One unresolved exports or opens directive, qualified when targets are present. */
public record JavaModulePackageDirective(
        JavaModulePackageDirectiveKind kind,
        JavaPackageName packageName,
        int flags,
        List<String> targetModules)
        implements Comparable<JavaModulePackageDirective> {
    public JavaModulePackageDirective {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(packageName, "packageName");
        if (packageName.isUnnamed()) throw new IllegalArgumentException("Module packages must be named");
        if (flags < 0 || flags > 0xffff) throw new IllegalArgumentException("flags must be an unsigned u2");
        Objects.requireNonNull(targetModules, "targetModules");
        targetModules = targetModules.stream()
                .map(value -> ModuleId.named(value).name())
                .sorted()
                .toList();
    }

    public boolean qualified() {
        return !targetModules.isEmpty();
    }

    @Override
    public int compareTo(JavaModulePackageDirective other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = packageName.compareTo(other.packageName);
        if (result != 0) return result;
        result = Integer.compareUnsigned(flags, other.flags);
        return result != 0 ? result : targetModules.toString().compareTo(other.targetModules.toString());
    }
}
