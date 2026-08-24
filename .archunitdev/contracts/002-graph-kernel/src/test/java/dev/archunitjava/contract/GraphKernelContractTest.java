package dev.archunitjava.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.GraphNode;
import dev.archunitjava.graph.GraphValidationException;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.ModuleId;
import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.TypeId;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class GraphKernelContractTest {
    @Test
    void identifiersHaveTypedStableKeysAndRejectAmbiguousText() {
        PackageId namedPackage = PackageId.named("com.example.api");
        PackageId unnamedPackage = PackageId.unnamed();
        TypeId type = TypeId.ofBinaryName("com.example.api.Outer$Inner");
        MemberId member = MemberId.of(type, "call", "(Ljava/lang/String;)V");
        ModuleId module = ModuleId.named("com.example.module");
        LocationId location = LocationId.ofResourcePath("classes/com/example/api/Outer$Inner.class");

        assertEquals("com.example.api", namedPackage.qualifiedName());
        assertTrue(unnamedPackage.isUnnamed());
        assertEquals("com.example.api.Outer$Inner", type.binaryName());
        assertEquals(type, member.owner());
        assertEquals("call", member.name());
        assertEquals("(Ljava/lang/String;)V", member.descriptor());
        assertEquals("com.example.module", module.name());
        assertEquals("classes/com/example/api/Outer$Inner.class", location.resourcePath());
        assertNotEquals(PackageId.named("same").stableKey(), TypeId.ofBinaryName("same").stableKey());

        rejects(List.of("", " ", ".com", "com.", "com..example", "com/example"), PackageId::named);
        rejects(List.of("", " ", ".Type", "com..Type", "com/Type", "Lcom.Type;", "[Lcom.Type;"),
                TypeId::ofBinaryName);
        rejects(List.of("", " ", ".module", "module.", "com..module", "com/module"), ModuleId::named);
        rejects(List.of("", " ", "/absolute.class", "C:/absolute.class", "../escape.class",
                "classes/../escape.class", "classes\\Type.class"), LocationId::ofResourcePath);

        assertThrows(IllegalArgumentException.class, () -> PackageId.named(null));
        assertThrows(IllegalArgumentException.class, () -> TypeId.ofBinaryName(null));
        assertThrows(IllegalArgumentException.class, () -> ModuleId.named(null));
        assertThrows(IllegalArgumentException.class, () -> LocationId.ofResourcePath(null));
        assertThrows(IllegalArgumentException.class, () -> MemberId.of(type, "", "I"));
        assertThrows(IllegalArgumentException.class, () -> MemberId.of(type, "bad name", "I"));
        assertThrows(IllegalArgumentException.class, () -> MemberId.of(type, "value", ""));
        assertThrows(IllegalArgumentException.class, () -> MemberId.of(type, "value", "bad descriptor"));
    }

    @Test
    void parallelEdgesMergeDuplicateFreeEvidenceAndIterateDeterministically() {
        TypeId api = TypeId.ofBinaryName("com.example.Api");
        TypeId isolated = TypeId.ofBinaryName("com.example.Isolated");
        TypeId service = TypeId.ofBinaryName("com.example.Service");
        MemberId caller = MemberId.of(api, "invoke", "()V");
        DependencyEvidence later = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/Z.class"), caller, 21, 8);
        DependencyEvidence earlier = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/A.class"), caller, 4, 3);

        DependencyGraph graph = DependencyGraph.builder()
                .addNode(service)
                .addNode(api)
                .addNode(isolated)
                .addDependency(api, service, DependencyKind.METHOD_CALL, later)
                .addDependency(api, service, DependencyKind.METHOD_CALL, earlier)
                .addDependency(api, service, DependencyKind.METHOD_CALL, earlier)
                .addDependency(api, service, DependencyKind.FIELD_TYPE, later)
                .build();

        assertEquals(List.of(api, isolated, service), graph.nodes().stream().map(GraphNode::id).toList());
        assertEquals(2, graph.edges().size());
        var methodCall = graph.edges().stream()
                .filter(edge -> edge.kind() == DependencyKind.METHOD_CALL)
                .findFirst()
                .orElseThrow();
        assertEquals(api, methodCall.origin());
        assertEquals(service, methodCall.target());
        assertEquals(List.of(earlier, later), methodCall.evidence());

        DependencyGraph reverseInsertion = DependencyGraph.builder()
                .addNode(isolated)
                .addNode(api)
                .addNode(service)
                .addDependency(api, service, DependencyKind.FIELD_TYPE, later)
                .addDependency(api, service, DependencyKind.METHOD_CALL, earlier)
                .addDependency(api, service, DependencyKind.METHOD_CALL, later)
                .build();
        assertEquals(graph.nodes(), reverseInsertion.nodes());
        assertEquals(graph.edges(), reverseInsertion.edges());
    }

    @Test
    void builtGraphsAreImmutableSnapshotsAndPreserveIsolatedNodes() {
        TypeId source = TypeId.ofBinaryName("com.example.Source");
        TypeId target = TypeId.ofBinaryName("com.example.Target");
        TypeId isolated = TypeId.ofBinaryName("com.example.Isolated");
        DependencyEvidence evidence = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/Source.class"));
        DependencyGraph.Builder builder = DependencyGraph.builder()
                .addNode(source)
                .addNode(target)
                .addNode(isolated)
                .addDependency(source, target, DependencyKind.METHOD_CALL, evidence);

        DependencyGraph snapshot = builder.build();
        builder.addNode(TypeId.ofBinaryName("com.example.AddedLater"));

        assertEquals(3, snapshot.nodes().size());
        assertTrue(snapshot.nodes().stream().map(GraphNode::id).anyMatch(isolated::equals));
        assertFalse(snapshot.nodes().stream().map(GraphNode::id)
                .anyMatch(TypeId.ofBinaryName("com.example.AddedLater")::equals));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.edges().getFirst().evidence().clear());
    }

    @Test
    void graphConstructionRejectsUnknownEndpoints() {
        TypeId known = TypeId.ofBinaryName("com.example.Known");
        TypeId missing = TypeId.ofBinaryName("com.example.Missing");
        DependencyEvidence evidence = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/com/example/Known.class"));

        GraphValidationException error = assertThrows(
                GraphValidationException.class,
                () -> DependencyGraph.builder()
                        .addNode(known)
                        .addDependency(known, missing, DependencyKind.METHOD_CALL, evidence)
                        .build());

        assertTrue(error.getMessage().contains(missing.stableKey()));
    }

    private static <T> void rejects(List<String> values, Function<String, T> factory) {
        for (String value : values) {
            assertThrows(IllegalArgumentException.class, () -> factory.apply(value), () -> "accepted " + value);
        }
    }
}
