package dev.archunitjava.model;

import dev.archunitjava.graph.ModuleId;
import java.lang.classfile.ClassFile;
import java.util.Optional;

/** One unresolved JPMS requires directive with its exact modifier mask. */
public record JavaModuleRequire(String moduleName, int flags, Optional<String> compiledVersion)
        implements Comparable<JavaModuleRequire> {
    public JavaModuleRequire {
        moduleName = ModuleId.named(moduleName).name();
        if (flags < 0 || flags > 0xffff) throw new IllegalArgumentException("flags must be an unsigned u2");
        if (compiledVersion == null) throw new NullPointerException("compiledVersion");
        compiledVersion = compiledVersion.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("compiledVersion must not be blank");
            return value;
        });
    }

    public boolean transitive() {
        return (flags & ClassFile.ACC_TRANSITIVE) != 0;
    }

    public boolean staticPhase() {
        return (flags & ClassFile.ACC_STATIC_PHASE) != 0;
    }

    @Override
    public int compareTo(JavaModuleRequire other) {
        int result = moduleName.compareTo(other.moduleName);
        if (result != 0) return result;
        result = Integer.compareUnsigned(flags, other.flags);
        return result != 0 ? result : compiledVersion.orElse("").compareTo(other.compiledVersion.orElse(""));
    }
}
