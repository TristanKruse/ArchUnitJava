package dev.archunitjava.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One unresolved service-provider declaration. */
public record JavaModuleProvide(
        JvmReferenceType service, List<JvmReferenceType> providers)
        implements Comparable<JavaModuleProvide> {
    public JavaModuleProvide {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(providers, "providers");
        providers = providers.stream()
                .map(value -> Objects.requireNonNull(value, "provider"))
                .sorted(Comparator.comparing(JvmReferenceType::binaryName))
                .toList();
        if (providers.isEmpty()) throw new IllegalArgumentException("providers must not be empty");
    }

    @Override
    public int compareTo(JavaModuleProvide other) {
        int result = service.binaryName().compareTo(other.service.binaryName());
        return result != 0 ? result : providers.toString().compareTo(other.providers.toString());
    }
}
