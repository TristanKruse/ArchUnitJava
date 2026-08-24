package dev.archunitjava.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class GraphKernelInvariantTest {
    @Test
    void publicEdgesNormalizeEvidenceAndOrderConsistentlyWithEquality() {
        TypeId type = TypeId.ofBinaryName("com.example.Type");
        DependencyEvidence earlier = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/A.class"));
        DependencyEvidence later = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/Z.class"));
        DependencyEdge withBoth = new DependencyEdge(
                type, type, DependencyKind.TYPE_REFERENCE, List.of(later, earlier, earlier));
        DependencyEdge withLater = new DependencyEdge(
                type, type, DependencyKind.TYPE_REFERENCE, List.of(later));

        assertEquals(List.of(earlier, later), withBoth.evidence());
        assertNotEquals(withBoth, withLater);
        assertNotEquals(0, withBoth.compareTo(withLater));
        assertEquals(2, new TreeSet<>(List.of(withBoth, withLater)).size());
    }

    @Test
    void builderRejectsConflictingIdentitiesWithTheSameStableKey() {
        CollidingId registered = new CollidingId("custom:same", 1);
        CollidingId conflicting = new CollidingId("custom:same", 2);
        DependencyEvidence evidence = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/Type.class"));
        DependencyGraph.Builder builder = DependencyGraph.builder()
                .addNode(registered)
                .addDependency(registered, registered, DependencyKind.TYPE_REFERENCE, evidence);

        assertThrows(IllegalArgumentException.class, () -> builder.addDependency(
                conflicting, registered, DependencyKind.TYPE_REFERENCE, evidence));
    }

    @Test
    void JavaKeywordsAreNotAcceptedAsQualifiedNameSegments() {
        assertThrows(IllegalArgumentException.class, () -> PackageId.named("com.class"));
        assertThrows(IllegalArgumentException.class, () -> TypeId.ofBinaryName("com.true"));
        assertThrows(IllegalArgumentException.class, () -> ModuleId.named("com._"));
    }

    private record CollidingId(String stableKey, int discriminator) implements StableId {}
}
