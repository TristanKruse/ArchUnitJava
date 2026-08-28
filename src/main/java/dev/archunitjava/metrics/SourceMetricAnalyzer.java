package dev.archunitjava.metrics;

import dev.archunitjava.graph.PackageId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.model.GeneratedCodeClassifier;
import dev.archunitjava.model.GeneratedCodeOptions;
import dev.archunitjava.model.JavaMember;
import dev.archunitjava.model.JavaMemberKind;
import dev.archunitjava.model.JavaMemberModifier;
import dev.archunitjava.model.JavaType;
import dev.archunitjava.model.TypeModelResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministic aggregation over imported declarations and caller-supplied source text. */
public final class SourceMetricAnalyzer {
    public SourceMetricReport analyze(
            TypeModelResult model, Collection<JavaSourceDocument> documents) {
        return analyze(model, documents, SourceMetricOptions.defaults());
    }

    public SourceMetricReport analyze(
            TypeModelResult model,
            Collection<JavaSourceDocument> documents,
            SourceMetricOptions options) {
        Objects.requireNonNull(model, "model");
        SourceMetricOptions policy = Objects.requireNonNull(options, "options");
        TreeMap<SourceDocumentId, JavaSourceDocument> sources = sources(documents);
        TreeMap<PackageId, Long> packageTypes = new TreeMap<>();
        TreeSet<SourceDocumentId> usedSources = new TreeSet<>();
        List<MetricSample> samples = new ArrayList<>();
        List<TypeId> missing = new ArrayList<>();
        List<TypeId> excludedGenerated = new ArrayList<>();
        GeneratedCodeOptions generatedOptions = GeneratedCodeOptions.enabled(
                policy.generatedAnnotationBinaryNames());

        long types = 0;
        long members = 0;
        long fields = 0;
        long methods = 0;
        long constructors = 0;
        long initializers = 0;
        long recordComponents = 0;
        for (JavaType type : model.types()) {
            if (!policy.includePackageInfoTypes()
                    && type.name().simpleName().equals("package-info")) continue;
            Optional<SourceDocumentId> sourceId = sourceId(type);
            Optional<JavaSourceDocument> source = sourceId.map(sources::get);
            boolean generated = new GeneratedCodeClassifier()
                    .classify(type, generatedOptions).generated()
                    || source.map(JavaSourceDocument::generated).orElse(false);
            TypeId typeId = TypeId.ofBinaryName(type.binaryName());
            if (generated && !policy.includeGeneratedTypes()) {
                excludedGenerated.add(typeId);
                continue;
            }

            types++;
            PackageId packageId = type.packageName().isUnnamed()
                    ? PackageId.unnamed() : PackageId.named(type.packageName().value());
            packageTypes.merge(packageId, 1L, Long::sum);
            if (source.isPresent()) usedSources.add(sourceId.orElseThrow());
            else missing.add(typeId);

            long typeFields = 0;
            long typeMethods = 0;
            long typeConstructors = 0;
            long typeInitializers = 0;
            for (JavaMember member : type.declaredMembers()) {
                if (!included(member, policy)) continue;
                switch (member.kind()) {
                    case FIELD -> typeFields++;
                    case METHOD -> typeMethods++;
                    case CONSTRUCTOR -> typeConstructors++;
                    case STATIC_INITIALIZER -> typeInitializers++;
                }
            }
            long typeMembers = typeFields + typeMethods + typeConstructors + typeInitializers;
            long typeComponents = type.recordComponents().size();
            members += typeMembers;
            fields += typeFields;
            methods += typeMethods;
            constructors += typeConstructors;
            initializers += typeInitializers;
            recordComponents += typeComponents;
            samples.add(sample(typeId, MetricName.TYPE_MEMBER_COUNT, typeMembers));
            samples.add(sample(typeId, MetricName.TYPE_FIELD_COUNT, typeFields));
            samples.add(sample(typeId, MetricName.TYPE_METHOD_COUNT, typeMethods));
            samples.add(sample(typeId, MetricName.TYPE_CONSTRUCTOR_COUNT, typeConstructors));
            samples.add(sample(typeId, MetricName.TYPE_STATIC_INITIALIZER_COUNT, typeInitializers));
            samples.add(sample(typeId, MetricName.TYPE_RECORD_COMPONENT_COUNT, typeComponents));
        }
        packageTypes.forEach((subject, count) ->
                samples.add(sample(subject, MetricName.PACKAGE_TYPE_COUNT, count)));

        long physical = 0;
        long blank = 0;
        long comment = 0;
        long code = 0;
        for (SourceDocumentId id : usedSources) {
            SourceLineMetrics lines = SourceLineMetrics.count(sources.get(id).content());
            physical += lines.physicalLines();
            blank += lines.blankLines();
            comment += lines.commentLines();
            code += lines.codeLines();
            samples.add(sample(id, MetricName.SOURCE_PHYSICAL_LINES, lines.physicalLines()));
            samples.add(sample(id, MetricName.SOURCE_BLANK_LINES, lines.blankLines()));
            samples.add(sample(id, MetricName.SOURCE_COMMENT_LINES, lines.commentLines()));
            samples.add(sample(id, MetricName.SOURCE_CODE_LINES, lines.codeLines()));
        }
        SourceCounts counts = new SourceCounts(
                packageTypes.size(), types, members, fields, methods, constructors, initializers,
                recordComponents, usedSources.size(), physical, blank, comment, code, missing.size());
        return new SourceMetricReport(counts, samples, missing, excludedGenerated);
    }

    private static TreeMap<SourceDocumentId, JavaSourceDocument> sources(
            Collection<JavaSourceDocument> documents) {
        TreeMap<SourceDocumentId, JavaSourceDocument> result = new TreeMap<>();
        for (JavaSourceDocument document : Objects.requireNonNull(documents, "documents")) {
            JavaSourceDocument value = Objects.requireNonNull(document, "sourceDocument");
            if (result.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("duplicate source document: " + value.id().stableKey());
            }
        }
        return result;
    }

    private static Optional<SourceDocumentId> sourceId(JavaType type) {
        return type.location().sourceFile().map(file ->
                new SourceDocumentId(type.packageName(), file));
    }

    private static boolean included(JavaMember member, SourceMetricOptions options) {
        if (options.includeSyntheticMembers()) return true;
        return !member.modifiers().contains(JavaMemberModifier.SYNTHETIC)
                && !member.modifiers().contains(JavaMemberModifier.BRIDGE);
    }

    private static MetricSample sample(
            dev.archunitjava.graph.StableId subject, MetricName metric, long value) {
        return new MetricSample(subject, metric, MetricAmount.of(value, metric.unit()));
    }
}
