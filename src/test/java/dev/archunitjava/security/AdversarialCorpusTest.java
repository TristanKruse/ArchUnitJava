package dev.archunitjava.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.baseline.BaselineJsonRenderer;
import dev.archunitjava.baseline.ReviewedBaseline;
import dev.archunitjava.baseline.Suppression;
import dev.archunitjava.cli.CliConfigurationException;
import dev.archunitjava.cli.CliConfigurationLoader;
import dev.archunitjava.diagram.plantuml.InvalidPlantUmlException;
import dev.archunitjava.diagram.plantuml.PlantUmlLimits;
import dev.archunitjava.diagram.plantuml.PlantUmlParser;
import dev.archunitjava.importer.AnalysisCacheKey;
import dev.archunitjava.importer.AnalysisCacheStatus;
import dev.archunitjava.importer.CacheInputFingerprint;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.ClassFileReaderOptions;
import dev.archunitjava.importer.InputDiagnosticCode;
import dev.archunitjava.importer.InputEnumerationOptions;
import dev.archunitjava.importer.SafeAnalysisCache;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.pattern.InvalidPatternException;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.report.D2GraphRenderer;
import dev.archunitjava.report.DotGraphRenderer;
import dev.archunitjava.report.GraphSnapshot;
import dev.archunitjava.report.GraphSnapshotLimits;
import dev.archunitjava.report.HtmlGraphRenderer;
import dev.archunitjava.report.HtmlRenderLimitException;
import dev.archunitjava.report.HtmlRenderLimits;
import dev.archunitjava.report.JsonGraphRenderer;
import dev.archunitjava.report.MermaidGraphRenderer;
import dev.archunitjava.report.ReportDomain;
import dev.archunitjava.report.SnapshotNode;
import dev.archunitjava.report.SnapshotQueryMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AdversarialCorpusTest {
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final ClassDesc OBJECT = ClassDesc.of("java.lang.Object");
    private static final ClassDesc STRING = ClassDesc.of("java.lang.String");
    private static final ClassDesc CALL_SITE = ClassDesc.of("java.lang.invoke.CallSite");
    private static final ClassDesc LOOKUP = ClassDesc.of("java.lang.invoke.MethodHandles$Lookup");
    private static final ClassDesc METHOD_TYPE = ClassDesc.of("java.lang.invoke.MethodType");

    @TempDir Path temporaryDirectory;

    @Test
    void importingStaticInitializersAndBootstrapHandlesNeverExecutesTargetCode() throws IOException {
        String property = "archunitjava.adversarial.tripwire";
        System.clearProperty(property);
        Tripwire.bootstrapCalls.set(0);
        byte[] bytes = executableLookingClass(property);
        Path file = temporaryDirectory.resolve("hostile/ExecutableLooking.class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);

        var enumeration = new ClassFileInputEnumerator().enumerate(
                List.of(ClassFileInput.directory(temporaryDirectory)));
        var model = new TypeModelBuilder().build(
                new ClassFileReader().readAll(enumeration.resources()));

        assertTrue(enumeration.diagnostics().isEmpty());
        assertTrue(model.classFileDiagnostics().isEmpty());
        assertEquals(1, model.types().size());
        assertEquals(1, model.types().getFirst().declaredMembers().stream()
                .flatMap(member -> member.dynamicCallSites().stream()).count());
        assertEquals(0, Tripwire.bootstrapCalls.get());
        assertEquals(null, System.getProperty(property));
    }

    @Test
    void hostileArchiveNamesNestingCompressionAndDiagnosticFloodsAreBounded() throws IOException {
        Path names = jar("names.jar", Map.of(
                "../escape.class", new byte[] {1},
                "/absolute.class", new byte[] {2},
                "nested.jar", new byte[] {3},
                "second.zip", new byte[] {4}));
        InputEnumerationOptions bounded = new InputEnumerationOptions(
                1, 16, 4, 16, 16, 1_000_000, 1_000_000, 200, 0, 128, 3);
        var namesResult = new ClassFileInputEnumerator(bounded)
                .enumerate(List.of(ClassFileInput.jar(names)));

        assertTrue(namesResult.resources().isEmpty());
        assertEquals(3, namesResult.diagnostics().size());
        assertTrue(namesResult.diagnostics().stream()
                .anyMatch(value -> value.code() == InputDiagnosticCode.DIAGNOSTIC_LIMIT_REACHED));

        Path compressed = jar("compressed.jar", Map.of("Bomb.class", new byte[64 * 1024]));
        InputEnumerationOptions ratioBound = new InputEnumerationOptions(
                1, 16, 4, 16, 16, 1_000_000, 1_000_000, 2, 0, 128, 8);
        var ratioResult = new ClassFileInputEnumerator(ratioBound)
                .enumerate(List.of(ClassFileInput.jar(compressed)));
        assertTrue(ratioResult.resources().isEmpty());
        assertEquals(InputDiagnosticCode.ARCHIVE_RESOURCE_REJECTED,
                ratioResult.diagnostics().getFirst().code());
    }

    @Test
    void pathsMetadataSelectorsAndMalformedBytecodeFailClosedWithinBudgets() throws IOException {
        Path approved = Files.createDirectory(temporaryDirectory.resolve("approved"));
        Files.write(temporaryDirectory.resolve("outside.jar"), new byte[] {1});
        Path configuration = approved.resolve("archunitjava.properties");
        Files.writeString(configuration,
                "schema=archunitjava.cli.v1\ninputs=../outside.jar\nrules=x\n",
                StandardCharsets.UTF_8);
        assertThrows(CliConfigurationException.class,
                () -> CliConfigurationLoader.load(configuration, approved));

        assertThrows(InvalidPatternException.class,
                () -> JavaPattern.regex(PatternDomain.QUALIFIED_NAME, "(?"));
        assertThrows(InvalidPlantUmlException.class,
                () -> PlantUmlParser.parse("!include https://attacker.invalid/payload\n",
                        Set.of(), new PlantUmlLimits(100, 10, 80, 4, 4)));

        Path broken = Files.createDirectory(temporaryDirectory.resolve("broken"));
        for (int index = 0; index < 20; index++) {
            Files.write(broken.resolve("Broken" + index + ".class"), new byte[] {1, 2, 3});
        }
        var resources = new ClassFileInputEnumerator().enumerate(
                List.of(ClassFileInput.directory(broken))).resources();
        var read = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> new ClassFileReader(new ClassFileReaderOptions(8, 4)).readAll(resources));
        assertTrue(read.classes().isEmpty());
        assertEquals(4, read.diagnostics().size());
    }

    @Test
    void serializedAndCorruptCachePayloadsRemainOpaqueAndSelfHeal() throws IOException {
        SerializedTripwire.readCalls.set(0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(new SerializedTripwire());
        }
        AnalysisCacheKey key = AnalysisCacheKey.create(
                List.of(CacheInputFingerprint.of("hostile", new byte[] {1})),
                25, "security", "parser", "library", 1);
        SafeAnalysisCache cache = new SafeAnalysisCache(temporaryDirectory.resolve("cache"), 4096);
        cache.loadOrCompute(key, bytes::toByteArray);
        cache.loadOrCompute(key, () -> { throw new AssertionError("cache recomputed"); });
        assertEquals(0, SerializedTripwire.readCalls.get());

        byte[] envelope = Files.readAllBytes(cache.entryPath(key));
        envelope[envelope.length - 1] ^= 1;
        Files.write(cache.entryPath(key), envelope);
        var replaced = cache.loadOrCompute(key, () -> new byte[] {9});
        assertEquals(AnalysisCacheStatus.CORRUPT_REPLACED, replaced.status());
        assertTrue(Arrays.equals(new byte[] {9}, replaced.payload()));
        assertEquals(0, SerializedTripwire.readCalls.get());
    }

    @Test
    void renderersEscapeTargetStringsAndEnforceExplicitOutputBudgets() {
        String hostile = "x\"]\nURL=\"file:///etc/passwd\"\n</script><script>alert(1)</script>";
        GraphSnapshot snapshot = snapshot(hostile);
        String html = HtmlGraphRenderer.render(snapshot);
        String json = JsonGraphRenderer.render(snapshot);
        String mermaid = MermaidGraphRenderer.render(snapshot);
        String d2 = D2GraphRenderer.render(snapshot);
        String dot = DotGraphRenderer.render(snapshot);

        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("default-src 'none'"));
        assertFalse(json.contains("</script>"));
        assertTrue(json.contains("\\u003c/script\\u003e"));
        assertFalse(mermaid.contains("</script>"));
        assertFalse(d2.contains("</script>"));
        assertFalse(dot.contains("\nURL=\"file:///etc/passwd"));
        assertThrows(HtmlRenderLimitException.class,
                () -> HtmlGraphRenderer.render(snapshot, new HtmlRenderLimits(1, 1, 1, 4)));
    }

    @Test
    void baselineValuesRejectUnknownSchemasAndDuplicateScopeBeforeRendering() {
        Suppression hostile = Suppression.forRule(
                "known", "</script>\n=cmd", "rule", Optional.empty());
        String json = BaselineJsonRenderer.render(ReviewedBaseline.of(List.of(), List.of(hostile)));
        assertFalse(json.contains("</script>"));
        assertTrue(json.contains("\\u003c/script\\u003e\\n=cmd"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewedBaseline("attacker.v999", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ReviewedBaseline.of(List.of(), List.of(hostile, hostile)));
    }

    @Test
    void manifestKeepsEveryThreatSurfaceReviewable() throws IOException {
        List<String> lines = Files.readAllLines(
                Path.of("test-fixtures", "adversarial", "corpus.tsv"), StandardCharsets.UTF_8)
                .stream().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        assertEquals(16, lines.size());
        for (String stage : List.of("classfile", "archive", "configuration", "metadata",
                "selector", "cache", "renderer", "baseline")) {
            assertTrue(lines.stream().anyMatch(line -> line.split("\\t", -1)[1].equals(stage)),
                    "missing adversarial stage " + stage);
        }
    }

    private byte[] executableLookingClass(String property) {
        DirectMethodHandleDesc bootstrap = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC,
                ClassDesc.of(Tripwire.class.getName()),
                "bootstrap",
                MethodTypeDesc.of(CALL_SITE, LOOKUP, STRING, METHOD_TYPE));
        DynamicCallSiteDesc callSite = DynamicCallSiteDesc.of(
                bootstrap, "payload", MethodTypeDesc.of(OBJECT));
        return ClassFile.of().build(ClassDesc.of("hostile.ExecutableLooking"), builder -> builder
                .withMethodBody("<clinit>", MethodTypeDesc.of(VOID), ClassFile.ACC_STATIC,
                        code -> code.ldc(property).ldc("executed")
                                .invokestatic(ClassDesc.of("java.lang.System"), "setProperty",
                                        MethodTypeDesc.of(STRING, STRING, STRING))
                                .pop().return_())
                .withMethodBody("dynamic", MethodTypeDesc.of(VOID),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        code -> code.invokedynamic(callSite).pop().return_()));
    }

    private static GraphSnapshot snapshot(String hostile) {
        SnapshotNode node = new SnapshotNode(hostile, hostile, 1, List.of(hostile));
        SnapshotQueryMetadata query = new SnapshotQueryMetadata(
                ReportDomain.TYPE, List.of(), List.of(), List.of(), false,
                0, 1, 0, 1, 1, 0, 0, 0, 0, new GraphSnapshotLimits(1, 1, 1));
        return new GraphSnapshot(query, List.of(node), List.of());
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return archive;
    }

    public static final class Tripwire {
        static final AtomicInteger bootstrapCalls = new AtomicInteger();

        private Tripwire() {}

        public static java.lang.invoke.CallSite bootstrap(
                java.lang.invoke.MethodHandles.Lookup lookup, String name,
                java.lang.invoke.MethodType type) {
            bootstrapCalls.incrementAndGet();
            throw new AssertionError("target bootstrap method executed");
        }
    }

    private static final class SerializedTripwire implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        static final AtomicInteger readCalls = new AtomicInteger();

        @Serial
        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            readCalls.incrementAndGet();
            input.defaultReadObject();
        }
    }
}
