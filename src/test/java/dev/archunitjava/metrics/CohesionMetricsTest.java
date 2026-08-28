package dev.archunitjava.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelBuilder;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CohesionMetricsTest {
    private static final ClassDesc TYPE = ClassDesc.of("p.Cohesive");
    private static final ClassDesc INCOMPLETE = ClassDesc.of("p.Incomplete");
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    @TempDir Path classes;
    private List<JavaType> types;

    @BeforeEach
    void fixture() throws IOException {
        write("p.Cohesive", ClassFile.of().build(TYPE, builder -> builder
                .withField("x", INT, 0)
                .withField("y", INT, 0)
                .withMethodBody("a", MethodTypeDesc.of(VOID), 0,
                        code -> code.aload(0).getfield(TYPE, "x", INT).pop().return_())
                .withMethodBody("b", MethodTypeDesc.of(VOID), 0,
                        code -> code.aload(0).getfield(TYPE, "x", INT).pop()
                                .aload(0).invokevirtual(TYPE, "c", MethodTypeDesc.of(VOID)).return_())
                .withMethodBody("c", MethodTypeDesc.of(VOID), 0,
                        code -> code.aload(0).getfield(TYPE, "y", INT).pop().return_())));
        write("p.Incomplete", ClassFile.of().build(INCOMPLETE, builder -> builder
                .withFlags(ClassFile.ACC_ABSTRACT)
                .withField("value", INT, 0)
                .withMethod("a", MethodTypeDesc.of(VOID), ClassFile.ACC_ABSTRACT, ignored -> {})
                .withMethod("b", MethodTypeDesc.of(VOID), ClassFile.ACC_ABSTRACT, ignored -> {})));
        var resources = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(classes))).resources();
        types = new TypeModelBuilder().build(new ClassFileReader().readAll(resources)).types();
    }

    @Test
    void formulaFixturesDistinguishPairCallGraphAndNormalizedVariants() {
        CohesionMetricReport report = new CohesionMetricAnalyzer().analyze(types);

        assertEquals(1.0, amount(report, "p.Cohesive", LcomVariant.CK_LCOM1));
        assertEquals(1.0, amount(report, "p.Cohesive", LcomVariant.LCOM4));
        assertEquals(0.75, amount(report, "p.Cohesive", LcomVariant.HENDERSON_SELLERS));
        assertEquals(3, value(report, "p.Cohesive", LcomVariant.LCOM4).methodCount());
        assertEquals(2, value(report, "p.Cohesive", LcomVariant.LCOM4).fieldCount());
    }

    @Test
    void absentMethodBodiesProduceIncompleteEvidenceInsteadOfInventedValues() {
        for (LcomVariant variant : LcomVariant.values()) {
            CohesionValue value = value(report(), "p.Incomplete", variant);
            assertEquals(MetricAvailability.INCOMPLETE_EVIDENCE, value.availability());
            assertEquals(true, value.amount().isEmpty());
        }
    }

    private CohesionMetricReport report() {
        return new CohesionMetricAnalyzer().analyze(types);
    }

    private static double amount(
            CohesionMetricReport report, String type, LcomVariant variant) {
        return value(report, type, variant).amount().orElseThrow().value().doubleValue();
    }

    private static CohesionValue value(
            CohesionMetricReport report, String type, LcomVariant variant) {
        return report.values().stream()
                .filter(value -> value.subject().binaryName().equals(type)
                        && value.variant() == variant)
                .findFirst().orElseThrow();
    }

    private void write(String binaryName, byte[] bytes) throws IOException {
        Path target = classes.resolve(binaryName.replace('.', '/') + ".class");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }
}
