package dev.archunitjava.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.MemberId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.importer.ClassFileInput;
import dev.archunitjava.importer.ClassFileInputEnumerator;
import dev.archunitjava.importer.ClassFileReader;
import dev.archunitjava.importer.InputDiagnostic;
import dev.archunitjava.junit.ArchitectureTestCases;
import dev.archunitjava.model.DeclarationDependencyExtractor;
import dev.archunitjava.model.JavaCodeAccessKind;
import dev.archunitjava.model.JavaMemberSignature;
import dev.archunitjava.model.JvmArrayType;
import dev.archunitjava.model.JvmReferenceType;
import dev.archunitjava.model.JvmType;
import dev.archunitjava.model.TypeModelBuilder;
import dev.archunitjava.model.TypeModelResult;
import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import dev.archunitjava.rules.ArchitectureRule;
import dev.archunitjava.rules.DependencyRuleMode;
import dev.archunitjava.rules.DependencyRuleSpec;
import dev.archunitjava.rules.DependencyRules;
import dev.archunitjava.rules.ExternalDependencyPolicy;
import dev.archunitjava.rules.SelfDependencyPolicy;
import dev.archunitjava.selector.PackageSelector;
import dev.archunitjava.selector.TypeSelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class ArchitectureDogfoodTest {
    private SelfArchitecture architecture;

    @BeforeAll
    void importOwnMainClasses() {
        architecture = importArchitecture();
    }

    @Test
    void bootstrapImportIsCompleteAndContainsIntegrationEntryPoints() {
        assertEquals(List.of(), architecture.inputDiagnostics());
        assertEquals(List.of(), architecture.model().classFileDiagnostics());
        assertEquals(List.of(), architecture.model().diagnostics());
        assertFalse(architecture.model().types().isEmpty());
        assertTrue(architecture.model().types().stream()
                .anyMatch(type -> type.binaryName().equals(
                        "dev.archunitjava.integration.MavenBuildIntegration")));
    }

    @TestFactory
    List<DynamicTest> documentedBoundariesUsePublicArchitectureRules() {
        return ArchitectureTestCases.forRules(boundaryRules()).stream()
                .map(testCase -> DynamicTest.dynamicTest(
                        testCase.displayName(), testCase::execute))
                .toList();
    }

    private List<ArchitectureRule> boundaryRules() {
        TypeModelResult model = architecture.model();
        DependencyGraph graph = architecture.graph();
        List<ArchitectureRule> rules = new ArrayList<>();
        rules.add(noTypeDependencies(
                "Graph kernel has no upward dependencies",
                packages("graph"),
                packages("importer", "model", "projection", "metrics", "rules", "report",
                        "baseline", "cli", "junit", "integration")));
        rules.add(noTypeDependencies(
                "Extraction does not depend on policy, presentation, or integrations",
                packages("importer", "model"),
                packages("metrics", "rules", "report", "baseline", "presets", "cli", "junit",
                        "integration")));
        rules.add(noTypeDependencies(
                "Projection stays independent of extraction and higher layers",
                packages("projection"),
                packages("importer", "model", "metrics", "rules", "report", "baseline",
                        "presets", "cli", "junit", "integration")));
        rules.add(noTypeDependencies(
                "Rules do not depend on reporting or integrations",
                packages("rules"),
                packages("report", "baseline", "presets", "cli", "junit", "integration")));
        rules.add(noTypeDependencies(
                "Reporting does not reach into extraction, analysis, policy, or integrations",
                packages("report"),
                packages("importer", "model", "projection", "metrics", "rules", "baseline",
                        "presets", "cli", "junit", "integration")));
        rules.add(noTypeDependencies(
                "Core packages do not depend on integration seams",
                packages("graph", "importer", "model", "projection", "rules", "report"),
                packages("cli", "junit", "integration")));
        rules.add(noTypeDependencies(
                "CLI does not depend on JUnit or build bridges",
                packages("cli"),
                packages("junit", "integration")));
        rules.add(noTypeDependencies(
                "JUnit does not depend on CLI or build bridges",
                packages("junit"),
                packages("cli", "integration")));
        rules.add(DependencyRules.packages(
                        model,
                        graph,
                        packageNamed("integration"),
                        packageNamed("cli"),
                        new DependencyRuleSpec(
                                DependencyRuleMode.ONLY,
                                SelfDependencyPolicy.IGNORE,
                                ExternalDependencyPolicy.IGNORE))
                .as("Build bridges depend internally only on the CLI seam")
                .because("build integrations must remain thin and must not become a second analyzer"));
        return List.copyOf(rules);
    }

    private ArchitectureRule noTypeDependencies(
            String displayName, TypeSelector origins, TypeSelector targets) {
        return DependencyRules.types(
                        architecture.model(),
                        architecture.graph(),
                        origins,
                        targets,
                        new DependencyRuleSpec(
                                DependencyRuleMode.NO,
                                SelfDependencyPolicy.IGNORE,
                                ExternalDependencyPolicy.IGNORE))
                .as(displayName)
                .because("the documented dependency direction must remain executable");
    }

    private static TypeSelector packages(String... suffixes) {
        return TypeSelector.anyOf(Arrays.stream(suffixes)
                .map(ArchitectureDogfoodTest::packageTypes)
                .toList());
    }

    private static TypeSelector packageTypes(String suffix) {
        String name = "dev.archunitjava." + suffix;
        return TypeSelector.anyOf(
                TypeSelector.packageName(JavaPattern.exact(PatternDomain.QUALIFIED_NAME, name)),
                TypeSelector.packageName(JavaPattern.glob(
                        PatternDomain.QUALIFIED_NAME, name + ".**")));
    }

    private static PackageSelector packageNamed(String suffix) {
        return PackageSelector.name(JavaPattern.exact(
                PatternDomain.QUALIFIED_NAME, "dev.archunitjava." + suffix));
    }

    private static SelfArchitecture importArchitecture() {
        Path classes = Path.of("target", "classes").toAbsolutePath().normalize();
        if (!Files.isDirectory(classes)) {
            throw new AssertionError("Compiled main classes are required before dogfood tests: " + classes);
        }
        var enumeration = new ClassFileInputEnumerator()
                .enumerate(List.of(ClassFileInput.directory(classes)));
        TypeModelResult model = new TypeModelBuilder().build(
                new ClassFileReader().readAll(enumeration.resources()));
        return new SelfArchitecture(model, graph(model), enumeration.diagnostics());
    }

    private static DependencyGraph graph(TypeModelResult model) {
        DependencyGraph.Builder graph = DependencyGraph.builder();
        model.types().forEach(type -> graph.addNode(TypeId.ofBinaryName(type.binaryName())));
        new DeclarationDependencyExtractor().extract(model.types()).dependencies()
                .forEach(dependency -> {
                    TypeId origin = TypeId.ofBinaryName(dependency.origin().binaryName());
                    TypeId target = TypeId.ofBinaryName(dependency.target().binaryName());
                    graph.addNode(origin).addNode(target);
                    dependency.sources().forEach(source -> graph.addDependency(
                            origin,
                            target,
                            DependencyKind.TYPE_REFERENCE,
                            DependencyEvidence.at(source.location().resource().locationId())));
                });
        model.types().forEach(type -> type.declaredMembers().forEach(member ->
                member.codeAccesses().forEach(access -> targetType(access.target().ownerType())
                        .ifPresent(targetName -> {
                            TypeId origin = TypeId.ofBinaryName(type.binaryName());
                            TypeId target = TypeId.ofBinaryName(targetName);
                            graph.addNode(origin).addNode(target);
                            JavaMemberSignature signature = member.signature();
                            graph.addDependency(
                                    origin,
                                    target,
                                    accessKind(access.kind()),
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

    private record SelfArchitecture(
            TypeModelResult model,
            DependencyGraph graph,
            List<InputDiagnostic> inputDiagnostics) {
        private SelfArchitecture {
            inputDiagnostics = inputDiagnostics.stream().sorted().toList();
        }
    }
}
