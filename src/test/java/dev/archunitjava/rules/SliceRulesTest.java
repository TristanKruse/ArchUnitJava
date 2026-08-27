package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.result.RuleStatus;
import dev.archunitjava.selector.TypeSelector;
import dev.archunitjava.slices.SliceCapturePattern;
import dev.archunitjava.slices.SliceDefinitions;
import dev.archunitjava.slices.SliceId;
import dev.archunitjava.slices.SliceMembershipException;
import dev.archunitjava.slices.SliceModel;
import dev.archunitjava.slices.SliceOverlapPolicy;
import dev.archunitjava.slices.SliceSelector;
import dev.archunitjava.slices.UnmatchedSlicePolicy;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SliceRulesTest {
    private static final TypeId ORDER = TypeId.ofBinaryName("com.acme.orders.api.Order");
    private static final TypeId HIDDEN = TypeId.ofBinaryName("com.acme.orders.internal.Hidden");
    private static final TypeId INVOICE = TypeId.ofBinaryName("com.acme.billing.Invoice");
    private static final TypeId SHARED = TypeId.ofBinaryName("shared.Util");
    private static final TypeId OUTSIDE = TypeId.ofBinaryName("outside.Unassigned");
    private static final MemberId ORDER_WORK = MemberId.of(ORDER, "work", "()V");

    @TempDir Path temporaryDirectory;
    private TypeModelResult model;

    @BeforeEach
    void importTypes() throws IOException {
        writeType(ORDER.binaryName());
        writeType(HIDDEN.binaryName());
        writeType(INVOICE.binaryName());
        writeType(SHARED.binaryName());
        writeType(OUTSIDE.binaryName());
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(temporaryDirectory)))
                .resources();
        model = new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }

    @Test
    void capturePatternsAndExplicitSelectorsProduceStableMemberships() {
        SliceModel slices = definitions().build(model);

        assertEquals(List.of("billing", "orders", "shared"), slices.slices().stream()
                .map(value -> value.id().name()).toList());
        assertEquals(SliceId.named("orders"), slices.sliceOf(ORDER).orElseThrow());
        assertEquals(SliceId.named("orders"), slices.sliceOf(HIDDEN).orElseThrow());
        assertEquals(SliceId.named("billing"), slices.sliceOf(INVOICE).orElseThrow());
        assertEquals(SliceId.named("shared"), slices.sliceOf(SHARED).orElseThrow());
        assertEquals(List.of(OUTSIDE), slices.unmatchedTypes());
        assertEquals(slices.definitionKey(), definitions().build(model).definitionKey());
    }

    @Test
    void overlapAndUnmatchedPoliciesAreExplicitAndDeterministic() {
        var overlapping = SliceDefinitions.builder()
                .capturePackages(SliceCapturePattern.of("com.acme.{slice}.."))
                .assign("aaa", packages("com.acme.orders.**"));
        assertThrows(SliceMembershipException.class, () -> overlapping.build(model));

        SliceModel firstByName = overlapping
                .overlapPolicy(SliceOverlapPolicy.FIRST_BY_NAME)
                .build(model);
        assertEquals(SliceId.named("aaa"), firstByName.sliceOf(ORDER).orElseThrow());
        assertEquals(SliceId.named("aaa"), firstByName.sliceOf(HIDDEN).orElseThrow());

        assertThrows(SliceMembershipException.class, () -> definitions()
                .unmatchedPolicy(UnmatchedSlicePolicy.FAIL)
                .build(model));
        assertThrows(IllegalArgumentException.class,
                () -> SliceCapturePattern.of("com.{slice}.{slice}"));
    }

    @Test
    void directionalRulesPreserveUnderlyingTypeAndMemberEvidence() {
        SliceModel slices = definitions().build(model);
        DependencyEvidence orderToInvoice = evidence(ORDER_WORK, 10);
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(ORDER).addNode(HIDDEN).addNode(INVOICE).addNode(SHARED).addNode(OUTSIDE)
                .addDependency(ORDER, INVOICE, DependencyKind.METHOD_CALL, orderToInvoice)
                .addDependency(HIDDEN, ORDER, DependencyKind.FIELD_TYPE, evidence(null, 20))
                .build();

        var forbidden = SliceRules.noDependencies(
                slices,
                graph,
                SliceSelector.named("orders"),
                SliceSelector.named("billing")).check();
        var reverse = SliceRules.noDependencies(
                slices,
                graph,
                SliceSelector.named("billing"),
                SliceSelector.named("orders")).check();
        var callsExcluded = SliceRules.noDependencies(
                slices,
                graph,
                SliceSelector.named("orders"),
                SliceSelector.named("billing"),
                EnumSet.of(DependencyKind.FIELD_TYPE)).check();

        assertEquals(RuleStatus.FAILED, forbidden.status());
        assertEquals(List.of(orderToInvoice), forbidden.violations().getFirst().evidence());
        assertEquals("[member:com.acme.orders.api.Order#work()V]",
                forbidden.violations().getFirst().attributes().get("underlyingMembers"));
        assertEquals(
                "[type:com.acme.orders.api.Order->type:com.acme.billing.Invoice:METHOD_CALL]",
                forbidden.violations().getFirst().attributes().get("underlyingTypeEdges"));
        assertEquals(RuleStatus.PASSED, reverse.status());
        assertEquals(RuleStatus.PASSED, callsExcluded.status());
    }

    @Test
    void mutualIndependenceEvaluatesEveryDistinctSelectedPair() {
        SliceModel slices = definitions().build(model);
        DependencyGraph graph = DependencyGraph.builder()
                .addNode(ORDER).addNode(HIDDEN).addNode(INVOICE).addNode(SHARED).addNode(OUTSIDE)
                .addDependency(ORDER, INVOICE, DependencyKind.METHOD_CALL, evidence(ORDER_WORK, 10))
                .addDependency(INVOICE, SHARED, DependencyKind.FIELD_TYPE, evidence(null, 20))
                .addDependency(SHARED, ORDER, DependencyKind.TYPE_REFERENCE, evidence(null, 30))
                .build();

        var result = SliceRules.mutuallyIndependent(
                slices, graph, SliceSelector.all()).check();

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(3, result.violations().size());
        assertEquals(List.of(
                        "[slice:billing->slice:shared]",
                        "[slice:orders->slice:billing]",
                        "[slice:shared->slice:orders]"),
                result.violations().stream()
                        .map(value -> value.attributes().get("directions"))
                        .sorted().toList());

        var missing = SliceRules.mutuallyIndependent(
                slices, graph, SliceSelector.named("missing")).check();
        assertEquals(RuleStatus.INCOMPLETE, missing.status());
        assertTrue(missing.diagnostics().stream()
                .anyMatch(value -> value.code().equals(RuleTerminal.EMPTY_SELECTION_CODE)));
    }

    private SliceDefinitions.Builder definitions() {
        return SliceDefinitions.builder()
                .capturePackages(SliceCapturePattern.of("com.acme.{slice}.."))
                .assign("shared", binary("shared.Util"));
    }

    private static TypeSelector binary(String value) {
        return TypeSelector.binaryName(JavaPattern.exact(PatternDomain.QUALIFIED_NAME, value));
    }

    private static TypeSelector packages(String value) {
        return TypeSelector.packageName(JavaPattern.glob(PatternDomain.QUALIFIED_NAME, value));
    }

    private static DependencyEvidence evidence(MemberId owner, int line) {
        return new DependencyEvidence(
                LocationId.ofResourcePath("classes/Fixture.class"),
                java.util.Optional.ofNullable(owner),
                java.util.OptionalInt.empty(),
                java.util.Optional.of("Fixture.java"),
                java.util.OptionalInt.of(line));
    }

    private void writeType(String binaryName) throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.of(binaryName), builder -> builder
                .withMethodBody(
                        "work",
                        MethodTypeDesc.of(ClassDesc.ofDescriptor("V")),
                        ClassFile.ACC_PUBLIC,
                        code -> code.return_()));
        Path target = temporaryDirectory.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
