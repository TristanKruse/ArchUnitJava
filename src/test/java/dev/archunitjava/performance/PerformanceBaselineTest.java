package dev.archunitjava.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.AnalysisCacheKey;
import dev.archunitjava.importer.CacheInputFingerprint;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.SafeAnalysisCache;
import dev.archunitjava.metrics.AbstractnessScope;
import dev.archunitjava.metrics.ComponentCompositions;
import dev.archunitjava.metrics.DependencyMetricAnalyzer;
import dev.archunitjava.model.DeclarationDependencyExtractor;
import dev.archunitjava.model.JavaCodeAccessKind;
import dev.archunitjava.model.JavaMemberSignature;
import dev.archunitjava.model.JvmArrayType;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.JvmType;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.projection.ProjectionPlan;
import dev.archunitjava.report.GraphSnapshot;
import dev.archunitjava.report.GraphSnapshotQuery;
import dev.archunitjava.report.HtmlGraphRenderer;
import dev.archunitjava.report.JsonGraphRenderer;
import dev.archunitjava.rules.DependencyRuleSpec;
import dev.archunitjava.rules.DependencyRules;
import dev.archunitjava.rules.ExternalDependencyPolicy;
import dev.archunitjava.selector.TypeSelector;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in, threshold-free benchmark with pinned inputs and semantic regression checks. */
final class PerformanceBaselineTest {
    private static final Path CORPUS_LOCK = Path.of("benchmarks", "corpora.lock");
    private static final Path SNAPSHOTS = Path.of("benchmarks", "model.snapshots");

    @TempDir Path temporaryDirectory;

    @Test
    void benchmarkPinnedOpenSourceCorporaWithoutExecutingThem() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("archunitjava.performance"),
                "enable with -Darchunitjava.performance=true");
        List<Corpus> corpora = loadCorpora();
        Map<String, String> expectedSnapshots = loadSnapshots();
        List<Run> runs = new ArrayList<>();
        for (Corpus corpus : corpora) {
            Path artifact = corpus.resolve();
            assertEquals(corpus.bytes(), Files.size(artifact), corpus.coordinate());
            assertEquals(corpus.sha256(), sha256(Files.readAllBytes(artifact)), corpus.coordinate());
            runs.add(measure(corpus.coordinate(), List.of(artifact), corpus.bytes()));
        }
        runs.add(measure("combined", corpora.stream().map(Corpus::resolve).toList(),
                corpora.stream().mapToLong(Corpus::bytes).sum()));

        Map<String, String> actualSnapshots = new LinkedHashMap<>();
        for (Run run : runs) {
            actualSnapshots.put(run.name(), run.semanticSha256());
            assertTrue(run.classes() > 0);
            assertTrue(run.members() > 0);
            assertTrue(run.reportBytes() > 0);
        }
        assertEquals(expectedSnapshots, actualSnapshots,
                "semantic model/diagnostic snapshots changed");
        String report = report(corpora, runs);
        Path output = Path.of("target", "benchmarks", "performance.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
        System.out.println(report);
    }

    private Run measure(String name, List<Path> artifacts, long bytes) throws Exception {
        long peak = usedHeap();
        long started = System.nanoTime();
        var enumeration = new ClassFileInputEnumerator().enumerate(
                artifacts.stream().map(ClassFileInput::jar).toList());
        TypeModelResult model = new TypeModelBuilder().build(
                new ClassFileReader().readAll(enumeration.resources()));
        long importNs = System.nanoTime() - started;
        peak = Math.max(peak, usedHeap());

        DependencyGraph graph = graph(model);
        GraphSnapshot snapshot = GraphSnapshotQuery.types(graph).snapshot();
        byte[] payload = semanticMaterial(model, snapshot, enumeration.diagnostics()).getBytes(
                StandardCharsets.UTF_8);
        String semanticSha256 = sha256(payload);
        List<CacheInputFingerprint> fingerprints = new ArrayList<>();
        for (Path artifact : artifacts) {
            fingerprints.add(CacheInputFingerprint.of(
                    artifact.getFileName().toString(), Files.readAllBytes(artifact)));
        }
        AnalysisCacheKey key = AnalysisCacheKey.create(
                fingerprints, 25, "performance-v1", "jdk-classfile-25", "0.1.0", 1);
        SafeAnalysisCache cache = new SafeAnalysisCache(
                temporaryDirectory.resolve("cache-" + sha256(name.getBytes(StandardCharsets.UTF_8))),
                Math.max(1, payload.length));

        started = System.nanoTime();
        cache.loadOrCompute(key, () -> payload);
        long cacheColdNs = System.nanoTime() - started;
        started = System.nanoTime();
        cache.loadOrCompute(key, () -> { throw new AssertionError("warm cache recomputed"); });
        long cacheWarmNs = System.nanoTime() - started;
        peak = Math.max(peak, usedHeap());

        started = System.nanoTime();
        DependencyRules.types(
                model, graph, TypeSelector.all(), TypeSelector.all(),
                DependencyRuleSpec.onlyDependencies()
                        .withExternalDependencies(ExternalDependencyPolicy.IGNORE))
                .check();
        long ruleNs = System.nanoTime() - started;

        started = System.nanoTime();
        var packages = ProjectionPlan.packages().apply(graph);
        new DependencyMetricAnalyzer().analyze(
                packages, ComponentCompositions.packages(
                        model.types(), AbstractnessScope.ALL_TYPES));
        long metricNs = System.nanoTime() - started;

        started = System.nanoTime();
        byte[] json = JsonGraphRenderer.renderBytes(snapshot);
        byte[] html = HtmlGraphRenderer.renderBytes(snapshot);
        long reportNs = System.nanoTime() - started;
        peak = Math.max(peak, usedHeap());
        int members = model.types().stream().mapToInt(type -> type.declaredMembers().size()).sum();
        return new Run(name, bytes, model.types().size(), members, graph.edges().size(),
                snapshot.edges().size(), (long) json.length + html.length, importNs, cacheColdNs,
                cacheWarmNs, ruleNs, metricNs, reportNs, peak, semanticSha256,
                enumeration.diagnostics().size() + model.classFileDiagnostics().size()
                        + model.diagnostics().size());
    }

    private static DependencyGraph graph(TypeModelResult model) {
        DependencyGraph.Builder graph = DependencyGraph.builder();
        model.types().forEach(type -> graph.addNode(TypeId.ofBinaryName(type.binaryName())));
        new DeclarationDependencyExtractor().extract(model.types()).dependencies().stream()
                .forEach(dependency -> {
            TypeId origin = TypeId.ofBinaryName(dependency.origin().binaryName());
            TypeId target = TypeId.ofBinaryName(dependency.target().binaryName());
            graph.addNode(origin).addNode(target);
            dependency.sources().forEach(source -> graph.addDependency(
                    origin, target, DependencyKind.TYPE_REFERENCE,
                    DependencyEvidence.at(source.location().resource().locationId())));
        });
        model.types().forEach(type -> type.declaredMembers().forEach(member ->
                member.codeAccesses().forEach(access -> targetType(access.target().ownerType())
                        .ifPresent(targetName -> {
                            TypeId origin = TypeId.ofBinaryName(type.binaryName());
                            TypeId target = TypeId.ofBinaryName(targetName);
                            graph.addNode(origin).addNode(target);
                            JavaMemberSignature signature = member.signature();
                            graph.addDependency(origin, target, accessKind(access.kind()),
                                    access.location().dependencyEvidence(MemberId.of(
                                            origin, signature.name(), signature.descriptor())));
                        }))));
        return graph.build();
    }

    private static Optional<String> targetType(JvmType type) {
        if (type instanceof JvmReferenceType reference) return Optional.of(reference.binaryName());
        if (type instanceof JvmArrayType array) return targetType(array.elementType());
        return Optional.empty();
    }

    private static DependencyKind accessKind(JavaCodeAccessKind kind) {
        return switch (kind) {
            case CONSTRUCTOR_CALL -> DependencyKind.CONSTRUCTOR_CALL;
            case FIELD_READ, FIELD_WRITE -> DependencyKind.FIELD_ACCESS;
            case METHOD_CALL -> DependencyKind.METHOD_CALL;
        };
    }

    private static String semanticMaterial(TypeModelResult model, GraphSnapshot snapshot,
            List<?> inputDiagnostics) {
        return model.types().stream().map(type -> type.binaryName() + "|"
                        + type.kind() + "|" + type.classFileVersion() + "|"
                        + type.declaredMembers().stream()
                                .map(member -> member.name() + member.descriptor()).toList())
                        .toList()
                + "\nmodules=" + model.modules()
                + "\ninput-diagnostics=" + inputDiagnostics
                + "\nclass-diagnostics=" + model.classFileDiagnostics()
                + "\nmodel-diagnostics=" + model.diagnostics()
                + "\nsnapshot=" + JsonGraphRenderer.render(snapshot);
    }

    private static List<Corpus> loadCorpora() throws IOException {
        return Files.readAllLines(CORPUS_LOCK, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> {
                    String[] fields = line.split("\\|", -1);
                    if (fields.length != 6) throw new IllegalArgumentException("invalid corpus lock");
                    return new Corpus(fields[0], fields[1], fields[2], Long.parseLong(fields[3]),
                            fields[4], fields[5]);
                }).toList();
    }

    private static Map<String, String> loadSnapshots() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        Files.readAllLines(SNAPSHOTS, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .forEach(line -> {
                    String[] fields = line.split("\\|", -1);
                    if (fields.length != 2 || result.putIfAbsent(fields[0], fields[1]) != null) {
                        throw new IllegalArgumentException("invalid model snapshot");
                    }
                });
        return Map.copyOf(result);
    }

    private static String report(List<Corpus> corpora, List<Run> runs) {
        var runtime = Runtime.getRuntime();
        StringBuilder out = new StringBuilder("{\n  \"schemaVersion\": \"archunitjava.performance.v1\",");
        out.append("\n  \"environment\": {")
                .append("\n    \"os\": \"").append(escape(System.getProperty("os.name"))).append("\",")
                .append("\n    \"architecture\": \"").append(escape(System.getProperty("os.arch"))).append("\",")
                .append("\n    \"processors\": ").append(runtime.availableProcessors()).append(',')
                .append("\n    \"java\": \"").append(escape(System.getProperty("java.version"))).append("\",")
                .append("\n    \"jvm\": \"").append(escape(System.getProperty("java.vm.name"))).append("\",")
                .append("\n    \"maxHeapBytes\": ").append(runtime.maxMemory()).append("\n  },")
                .append("\n  \"corpora\": [");
        for (int index = 0; index < corpora.size(); index++) {
            if (index > 0) out.append(',');
            Corpus corpus = corpora.get(index);
            out.append("\n    {\"coordinate\": \"").append(escape(corpus.coordinate()))
                    .append("\", \"version\": \"").append(escape(corpus.version()))
                    .append("\", \"sha256\": \"").append(corpus.sha256())
                    .append("\", \"bytes\": ").append(corpus.bytes()).append('}');
        }
        out.append("\n  ],\n  \"runs\": [");
        for (int index = 0; index < runs.size(); index++) {
            if (index > 0) out.append(',');
            Run run = runs.get(index);
            out.append("\n    {")
                    .append("\"name\": \"").append(escape(run.name())).append("\",")
                    .append(" \"inputBytes\": ").append(run.inputBytes()).append(',')
                    .append(" \"classes\": ").append(run.classes()).append(',')
                    .append(" \"members\": ").append(run.members()).append(',')
                    .append(" \"dependencyEdges\": ").append(run.dependencyEdges()).append(',')
                    .append(" \"reportEdges\": ").append(run.reportEdges()).append(',')
                    .append(" \"reportBytes\": ").append(run.reportBytes()).append(',')
                    .append(" \"importNs\": ").append(run.importNs()).append(',')
                    .append(" \"cacheColdNs\": ").append(run.cacheColdNs()).append(',')
                    .append(" \"cacheWarmNs\": ").append(run.cacheWarmNs()).append(',')
                    .append(" \"ruleNs\": ").append(run.ruleNs()).append(',')
                    .append(" \"metricNs\": ").append(run.metricNs()).append(',')
                    .append(" \"reportNs\": ").append(run.reportNs()).append(',')
                    .append(" \"peakObservedHeapBytes\": ").append(run.peakObservedHeapBytes()).append(',')
                    .append(" \"diagnostics\": ").append(run.diagnostics()).append(',')
                    .append(" \"semanticSha256\": \"").append(run.semanticSha256()).append("\"}");
        }
        return out.append("\n  ]\n}\n").toString();
    }

    private static long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private record Corpus(String coordinate, String version, String sha256, long bytes,
            String license, String source) {
        Path resolve() {
            String repository = System.getProperty("archunitjava.performance.repository",
                    Path.of(System.getProperty("user.home"), ".m2", "repository").toString());
            String[] parts = coordinate.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("invalid coordinate");
            return Path.of(repository, parts[0].replace('.', '/'), parts[1], version,
                    parts[1] + "-" + version + ".jar").toAbsolutePath().normalize();
        }
    }

    private record Run(String name, long inputBytes, int classes, int members,
            int dependencyEdges, int reportEdges, long reportBytes, long importNs,
            long cacheColdNs, long cacheWarmNs, long ruleNs, long metricNs, long reportNs,
            long peakObservedHeapBytes, String semanticSha256, int diagnostics) {}
}
