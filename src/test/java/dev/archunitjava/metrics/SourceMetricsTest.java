package dev.archunitjava.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.JavaPackageName;
import dev.archunitjava.model.SourceFileName;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.result.RuleStatus;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SourceMetricsTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    @TempDir Path classes;
    private TypeModelResult model;
    private List<JavaSourceDocument> documents;

    @BeforeEach
    void fixture() throws IOException {
        write("p.Example", ClassFile.of().build(ClassDesc.of("p.Example"), builder -> builder
                .with(SourceFileAttribute.of("Example.java"))
                .withField("value", INT, 0)
                .withField("$cache", INT, ClassFile.ACC_SYNTHETIC)
                .withMethodBody("work", MethodTypeDesc.of(VOID), 0, code -> code.return_())
                .withMethodBody("bridge", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE,
                        code -> code.return_())));
        write("p.Data", ClassFile.of().build(ClassDesc.of("p.Data"), builder -> builder
                .withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL)
                .withSuperclass(ClassDesc.of("java.lang.Record"))
                .with(SourceFileAttribute.of("Data.java"))
                .with(RecordAttribute.of(RecordComponentInfo.of("value", INT)))));
        write("p.Missing", ClassFile.of().build(ClassDesc.of("p.Missing"), builder -> {}));
        write("p.Generated", ClassFile.of().build(ClassDesc.of("p.Generated"), builder -> builder
                .withFlags(AccessFlag.PUBLIC, AccessFlag.SYNTHETIC)
                .with(SourceFileAttribute.of("Generated.java"))));
        model = importModel();
        documents = List.of(
                document("Example.java", String.join("\n",
                        "package p; // mixed code/comment",
                        "",
                        "// one comment",
                        "/* block",
                        " * still a comment",
                        " */",
                        "class Example {",
                        "  String marker = \"// not a comment\";",
                        "}") + "\n", false),
                document("Data.java", "package p;\nrecord Data(int value) {}\n", false),
                document("Generated.java", "package p; class Generated {}\n", true));
    }

    @Test
    void defaultsDefineSyntheticRecordGeneratedAndMissingSourceTreatment() {
        SourceMetricReport report = new SourceMetricAnalyzer().analyze(model, documents);

        assertEquals(new SourceCounts(
                1, 3, 2, 1, 1, 0, 0, 1, 2,
                11, 1, 4, 6, 1), report.aggregate());
        assertEquals(List.of(TypeId.ofBinaryName("p.Missing")), report.missingSourceTypes());
        assertEquals(List.of(TypeId.ofBinaryName("p.Generated")),
                report.excludedGeneratedTypes());
        assertEquals(3, amount(report, MetricName.PACKAGE_TYPE_COUNT, "package:p"));
        assertEquals(2, amount(report, MetricName.TYPE_MEMBER_COUNT, "type:p.Example"));
        assertEquals(1, amount(report, MetricName.TYPE_RECORD_COMPONENT_COUNT, "type:p.Data"));
        assertEquals(9, amount(report, MetricName.SOURCE_PHYSICAL_LINES,
                "source:p/Example.java"));
    }

    @Test
    void explicitOptionsCanIncludeGeneratedTypesAndSyntheticMembers() {
        SourceMetricReport report = new SourceMetricAnalyzer().analyze(
                model,
                documents,
                new SourceMetricOptions(true, true, false, Set.of()));

        assertEquals(4, report.aggregate().typeCount());
        assertEquals(4, report.aggregate().memberCount());
        assertEquals(3, report.aggregate().sourceFileCount());
        assertEquals(12, report.aggregate().physicalLineCount());
        assertEquals(7, report.aggregate().codeLineCount());
        assertEquals(List.of(), report.excludedGeneratedTypes());
    }

    @Test
    void lexicalLineCountingDoesNotTreatCommentMarkersInsideStringsAsComments() {
        SourceLineMetrics lines = SourceLineMetrics.count(
                "String marker = \"//\"; /* mixed */\n/*\n\n*/\n");

        assertEquals(new SourceLineMetrics(4, 0, 3, 1), lines);
        assertEquals(new SourceLineMetrics(0, 0, 0, 0), SourceLineMetrics.count(""));
    }

    @Test
    void thresholdRulesRejectWrongUnitsAndReportEveryViolatingSubject() {
        List<MetricSample> samples = List.of(
                sample("a.Small", 4),
                sample("b.Large", 7),
                sample("c.Larger", 9));
        MetricThreshold threshold = MetricThreshold.atMost(
                MetricName.TYPE_MEMBER_COUNT, MetricAmount.of(4, MetricUnit.MEMBERS));

        var result = MetricThresholdRules.enforce(samples, threshold).check(CheckOptions.defaults());

        assertEquals(RuleStatus.FAILED, result.status());
        assertEquals(List.of("type:b.Large", "type:c.Larger"), result.violations().stream()
                .map(violation -> violation.subjects().getFirst().id().stableKey()).toList());
        assertThrows(IllegalArgumentException.class, () -> MetricThreshold.atMost(
                MetricName.TYPE_MEMBER_COUNT, MetricAmount.of(4, MetricUnit.LINES)));
    }

    @Test
    void duplicateSourceIdentitiesAreRejectedRatherThanSilentlyMerged() {
        assertThrows(IllegalArgumentException.class, () -> new SourceMetricAnalyzer().analyze(
                model, List.of(documents.getFirst(), documents.getFirst())));
    }

    private static long amount(SourceMetricReport report, MetricName metric, String subject) {
        return report.samples().stream()
                .filter(sample -> sample.metric() == metric
                        && sample.subject().stableKey().equals(subject))
                .findFirst().orElseThrow().amount().value().longValueExact();
    }

    private static MetricSample sample(String type, long value) {
        return new MetricSample(
                TypeId.ofBinaryName(type),
                MetricName.TYPE_MEMBER_COUNT,
                MetricAmount.of(value, MetricUnit.MEMBERS));
    }

    private static JavaSourceDocument document(String file, String content, boolean generated) {
        return new JavaSourceDocument(
                new SourceDocumentId(new JavaPackageName("p"), new SourceFileName(file)),
                content,
                generated);
    }

    private void write(String binaryName, byte[] bytes) throws IOException {
        Path target = classes.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private TypeModelResult importModel() {
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(classes))).resources();
        return new TypeModelBuilder().build(new ClassFileReader().readAll(resources));
    }
}
