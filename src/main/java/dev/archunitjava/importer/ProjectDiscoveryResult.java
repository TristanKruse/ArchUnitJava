package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Immutable project-discovery outcome with deterministic diagnostics. */
public final class ProjectDiscoveryResult {
    private final Optional<DiscoveredProject> project;
    private final List<DiscoveryDiagnostic> diagnostics;

    ProjectDiscoveryResult(DiscoveredProject project, List<DiscoveryDiagnostic> diagnostics) {
        this.project = Optional.ofNullable(project);
        Objects.requireNonNull(diagnostics, "diagnostics");
        TreeSet<DiscoveryDiagnostic> sorted = new TreeSet<>();
        for (DiscoveryDiagnostic diagnostic : diagnostics) {
            sorted.add(Objects.requireNonNull(diagnostic, "diagnostic"));
        }
        this.diagnostics = List.copyOf(sorted);
    }

    public Optional<DiscoveredProject> project() {
        return project;
    }

    public List<DiscoveryDiagnostic> diagnostics() {
        return diagnostics;
    }

    public boolean found() {
        return project.isPresent();
    }
}
