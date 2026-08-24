package dev.archunitjava.importer;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Read-only, bounded discovery of explicit, Maven, and Gradle Java project roots. */
public final class ProjectDiscovery {
    static final int MAX_METADATA_BYTES = 1_048_576;
    private static final Pattern GRADLE_INCLUDE = Pattern.compile(
            "(?m)^\\h*include\\b\\h*(?:\\(([^)]*)\\)|([^;\\r\\n]+))\\h*;?\\h*$");
    private static final Pattern QUOTED_VALUE = Pattern.compile("['\"]([^'\"]+)['\"]");
    private static final Pattern CUSTOM_GRADLE_OUTPUT = Pattern.compile(
            "\\b(?:destinationDirectory|classesDirs|buildDir|buildDirectory)\\b");

    private ProjectDiscovery() {}

    public static ProjectDiscoveryResult discover(Path start) {
        return discover(start, ProjectDiscoveryOptions.defaults());
    }

    public static ProjectDiscoveryResult discover(
            Path start, ProjectDiscoveryOptions options) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(options, "options");
        List<DiscoveryDiagnostic> diagnostics = new ArrayList<>();

        if (options.explicitRoot().isPresent()) {
            Path root = options.explicitRoot().orElseThrow();
            if (!isDirectory(root)) {
                diagnostics.add(diagnostic(DiscoveryDiagnosticCode.INVALID_START, root, "kind", "explicit-root"));
                return new ProjectDiscoveryResult(null, diagnostics);
            }
            Candidate candidate = inspectCandidate(root, diagnostics);
            if (candidate == null) {
                return new ProjectDiscoveryResult(new DiscoveredProject(
                        root, BuildSystem.EXPLICIT, List.of(), List.of(), List.of()), diagnostics);
            }
            if (candidate.ambiguousBuild) {
                return new ProjectDiscoveryResult(new DiscoveredProject(
                        root, BuildSystem.EXPLICIT, List.of(), List.of(), candidate.metadataFiles), diagnostics);
            }
            if (!candidate.usable) {
                return new ProjectDiscoveryResult(new DiscoveredProject(
                        root, BuildSystem.EXPLICIT, List.of(), List.of(), candidate.metadataFiles), diagnostics);
            }
            return new ProjectDiscoveryResult(candidate.toProject(), diagnostics);
        }

        Path current = startingDirectory(start.toAbsolutePath().normalize());
        if (current == null) {
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.INVALID_START, start, "kind", "start"));
            return new ProjectDiscoveryResult(null, diagnostics);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int depth = 0; current != null && depth <= options.maxAncestorDepth(); depth++) {
            Candidate candidate = inspectCandidate(current, diagnostics);
            if (candidate != null) candidates.add(candidate);
            current = current.getParent();
        }
        if (candidates.isEmpty()) {
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.NO_PROJECT_FOUND, start,
                    "maxAncestorDepth", Integer.toString(options.maxAncestorDepth())));
            return new ProjectDiscoveryResult(null, diagnostics);
        }
        if (candidates.stream().anyMatch(candidate -> candidate.ambiguousBuild)) {
            return new ProjectDiscoveryResult(null, diagnostics);
        }
        if (candidates.stream().anyMatch(candidate -> !candidate.usable)) {
            return new ProjectDiscoveryResult(null, diagnostics);
        }
        if (candidates.size() == 1) {
            return new ProjectDiscoveryResult(candidates.getFirst().toProject(), diagnostics);
        }

        Optional<Candidate> multiProject = resolveMultiProject(candidates);
        if (multiProject.isPresent()) {
            return new ProjectDiscoveryResult(multiProject.orElseThrow().toProject(), diagnostics);
        }
        diagnostics.add(diagnostic(DiscoveryDiagnosticCode.AMBIGUOUS_PROJECT_ROOTS,
                candidates.getFirst().root, "candidates", candidates.stream()
                        .map(candidate -> candidate.root.toString()).sorted().reduce((a, b) -> a + "|" + b)
                        .orElseThrow()));
        return new ProjectDiscoveryResult(null, diagnostics);
    }

    private static Path startingDirectory(Path start) {
        if (isDirectory(start)) return start;
        return isRegularFile(start) ? start.getParent() : null;
    }

    private static Candidate inspectCandidate(Path root, List<DiscoveryDiagnostic> diagnostics) {
        Path pom = root.resolve("pom.xml");
        List<Path> gradleFiles = List.of(
                root.resolve("settings.gradle"), root.resolve("settings.gradle.kts"),
                root.resolve("build.gradle"), root.resolve("build.gradle.kts")).stream()
                .filter(ProjectDiscovery::isRegularFile).toList();
        boolean hasMaven = isRegularFile(pom);
        boolean hasGradle = !gradleFiles.isEmpty();
        if (!hasMaven && !hasGradle) return null;
        if (hasMaven && hasGradle) {
            List<Path> metadata = new ArrayList<>(gradleFiles);
            metadata.add(pom);
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.AMBIGUOUS_BUILD_METADATA, root,
                    "systems", "maven|gradle"));
            return Candidate.ambiguous(root, metadata);
        }
        if (hasMaven) return mavenCandidate(root, pom, diagnostics);
        long settingsVariants = gradleFiles.stream()
                .filter(path -> path.getFileName().toString().startsWith("settings.gradle"))
                .count();
        long buildVariants = gradleFiles.stream()
                .filter(path -> path.getFileName().toString().startsWith("build.gradle"))
                .count();
        if (settingsVariants > 1 || buildVariants > 1) {
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.AMBIGUOUS_BUILD_METADATA, root,
                    "systems", "gradle-variants"));
            return Candidate.ambiguous(root, gradleFiles);
        }
        return gradleCandidate(root, gradleFiles, diagnostics);
    }

    private static Candidate mavenCandidate(
            Path root, Path pom, List<DiscoveryDiagnostic> diagnostics) {
        Optional<byte[]> bytes = readMetadata(pom, diagnostics);
        if (bytes.isEmpty()) return Candidate.unusable(root, BuildSystem.MAVEN, List.of(pom));
        try {
            DocumentBuilderFactory factory = secureDocumentBuilderFactory();
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Element project = builder.parse(new ByteArrayInputStream(bytes.orElseThrow())).getDocumentElement();
            List<Path> modules = childTexts(directChild(project, "modules"), "module").stream()
                    .map(value -> safeDeclaredPath(root, pom, value, diagnostics, false))
                    .flatMap(Optional::stream).toList();
            Optional<String> declaredMain = childText(directChild(project, "build"), "outputDirectory");
            Optional<String> declaredTest = childText(directChild(project, "build"), "testOutputDirectory");
            boolean aggregatorOnly = childText(project, "packaging").filter("pom"::equals).isPresent();
            List<Path> main = aggregatorOnly && declaredMain.isEmpty()
                    ? List.of()
                    : outputPath(root, pom, declaredMain, "target/classes", diagnostics).stream().toList();
            List<Path> test = aggregatorOnly && declaredTest.isEmpty()
                    ? List.of()
                    : outputPath(root, pom, declaredTest, "target/test-classes", diagnostics).stream().toList();
            return Candidate.maven(root, pom, main, test, modules);
        } catch (SAXException | IOException | RuntimeException | javax.xml.parsers.ParserConfigurationException error) {
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.MALFORMED_METADATA, pom,
                    "error", error.getClass().getSimpleName()));
            return Candidate.unusable(root, BuildSystem.MAVEN, List.of(pom));
        }
    }

    private static Candidate gradleCandidate(
            Path root, List<Path> metadataFiles, List<DiscoveryDiagnostic> diagnostics) {
        Set<Path> modules = new HashSet<>();
        List<Path> settings = metadataFiles.stream()
                .filter(path -> path.getFileName().toString().startsWith("settings.gradle"))
                .toList();
        boolean usable = true;
        for (Path path : settings) {
            Optional<byte[]> bytes = readMetadata(path, diagnostics);
            if (bytes.isEmpty()) {
                usable = false;
                continue;
            }
            try {
                String text = retainTopLevelGradleStatements(stripGradleComments(
                        StandardCharsets.UTF_8.newDecoder()
                                .decode(ByteBuffer.wrap(bytes.orElseThrow())).toString()));
                Matcher include = GRADLE_INCLUDE.matcher(text);
                while (include.find()) {
                    String arguments = include.group(1) != null ? include.group(1) : include.group(2);
                    Matcher quoted = QUOTED_VALUE.matcher(arguments);
                    while (quoted.find()) {
                        String relative = quoted.group(1).replace(':', '/');
                        while (relative.startsWith("/")) relative = relative.substring(1);
                        safeDeclaredPath(root, path, relative, diagnostics, false).ifPresent(modules::add);
                    }
                }
            } catch (CharacterCodingException error) {
                diagnostics.add(diagnostic(DiscoveryDiagnosticCode.MALFORMED_METADATA, path,
                        "error", "invalid-utf8"));
                usable = false;
            }
        }
        boolean conventionalOutputs = true;
        for (Path path : metadataFiles) {
            if (!path.getFileName().toString().startsWith("build.gradle")) continue;
            Optional<byte[]> bytes = readMetadata(path, diagnostics);
            if (bytes.isEmpty()) {
                conventionalOutputs = false;
                continue;
            }
            try {
                String text = stripGradleComments(StandardCharsets.UTF_8.newDecoder()
                        .decode(ByteBuffer.wrap(bytes.orElseThrow())).toString());
                if (CUSTOM_GRADLE_OUTPUT.matcher(text).find()) {
                    diagnostics.add(diagnostic(DiscoveryDiagnosticCode.DYNAMIC_METADATA, path,
                            "feature", "custom-gradle-output"));
                    conventionalOutputs = false;
                }
            } catch (CharacterCodingException error) {
                diagnostics.add(diagnostic(DiscoveryDiagnosticCode.MALFORMED_METADATA, path,
                        "error", "invalid-utf8"));
                conventionalOutputs = false;
            }
        }
        List<Path> main = conventionalOutputs
                ? List.of(root.resolve("build/classes/java/main")) : List.of();
        List<Path> test = conventionalOutputs
                ? List.of(root.resolve("build/classes/java/test")) : List.of();
        return Candidate.gradle(root, metadataFiles, modules, main, test, usable);
    }

    private static Optional<Candidate> resolveMultiProject(List<Candidate> nearestFirst) {
        Set<BuildSystem> systems = new HashSet<>();
        nearestFirst.forEach(candidate -> systems.add(candidate.buildSystem));
        if (systems.size() != 1) return Optional.empty();
        List<Candidate> outerFirst = nearestFirst.reversed();
        Candidate outer = outerFirst.getFirst();
        if (outer.buildSystem == BuildSystem.GRADLE) {
            boolean declaresEveryNestedRoot = outer.declaredModules.containsAll(
                    outerFirst.subList(1, outerFirst.size()).stream().map(candidate -> candidate.root).toList());
            return declaresEveryNestedRoot ? Optional.of(Candidate.combine(outer, outerFirst)) : Optional.empty();
        }
        for (int index = 0; index < outerFirst.size() - 1; index++) {
            if (!outerFirst.get(index).declaredModules.contains(outerFirst.get(index + 1).root)) {
                return Optional.empty();
            }
        }
        return Optional.of(Candidate.combine(outer, outerFirst));
    }

    private static Optional<Path> outputPath(
            Path root,
            Path metadata,
            Optional<String> declared,
            String conventional,
            List<DiscoveryDiagnostic> diagnostics) {
        if (declared.isEmpty()) return Optional.of(root.resolve(conventional).normalize());
        return safeDeclaredPath(root, metadata, declared.orElseThrow(), diagnostics, true);
    }

    private static Optional<Path> safeDeclaredPath(
            Path root,
            Path metadata,
            String raw,
            List<DiscoveryDiagnostic> diagnostics,
            boolean output) {
        String value = raw.strip();
        if (value.isEmpty() || value.contains("${") || value.contains("$(") || value.contains("$")) {
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.DYNAMIC_METADATA, metadata,
                    "value", value.isEmpty() ? "<empty>" : value));
            return Optional.empty();
        }
        final Path resolved;
        try {
            resolved = root.resolve(value).toAbsolutePath().normalize();
        } catch (RuntimeException error) {
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.MALFORMED_METADATA, metadata,
                    "value", value));
            return Optional.empty();
        }
        if (!resolved.startsWith(root)) {
            diagnostics.add(diagnostic(
                    output ? DiscoveryDiagnosticCode.OUTPUT_OUTSIDE_ROOT
                            : DiscoveryDiagnosticCode.MALFORMED_METADATA,
                    metadata, "value", value));
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    private static Optional<byte[]> readMetadata(
            Path path, List<DiscoveryDiagnostic> diagnostics) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) return Optional.empty();
            if (attributes.size() > MAX_METADATA_BYTES) {
                diagnostics.add(diagnostic(DiscoveryDiagnosticCode.METADATA_TOO_LARGE, path,
                        "bytes", Long.toString(attributes.size())));
                return Optional.empty();
            }
            Set<OpenOption> options = Set.of(READ, NOFOLLOW_LINKS);
            try (SeekableByteChannel channel = Files.newByteChannel(path, options)) {
                ByteBuffer buffer = ByteBuffer.allocate((int) attributes.size());
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {}
                if (channel.read(ByteBuffer.allocate(1)) >= 0) {
                    diagnostics.add(diagnostic(DiscoveryDiagnosticCode.METADATA_TOO_LARGE, path,
                            "bytes", ">" + MAX_METADATA_BYTES));
                    return Optional.empty();
                }
                return Optional.of(buffer.array());
            }
        } catch (IOException | RuntimeException error) {
            diagnostics.add(diagnostic(DiscoveryDiagnosticCode.MALFORMED_METADATA, path,
                    "error", error.getClass().getSimpleName()));
            return Optional.empty();
        }
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory()
            throws javax.xml.parsers.ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static String stripGradleComments(String value) {
        StringBuilder cleaned = new StringBuilder(value.length());
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            char next = index + 1 < value.length() ? value.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    cleaned.append(current);
                } else {
                    cleaned.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    cleaned.append("  ");
                    index++;
                    blockComment = false;
                } else {
                    cleaned.append(current == '\n' || current == '\r' ? current : ' ');
                }
                continue;
            }
            if (!singleQuoted && !doubleQuoted && current == '/' && next == '/') {
                cleaned.append("  ");
                index++;
                lineComment = true;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && current == '/' && next == '*') {
                cleaned.append("  ");
                index++;
                blockComment = true;
                continue;
            }
            cleaned.append(current);
            if (escaped) {
                escaped = false;
            } else if ((singleQuoted || doubleQuoted) && current == '\\') {
                escaped = true;
            } else if (!doubleQuoted && current == '\'') {
                singleQuoted = !singleQuoted;
            } else if (!singleQuoted && current == '"') {
                doubleQuoted = !doubleQuoted;
            }
        }
        return cleaned.toString();
    }

    private static String retainTopLevelGradleStatements(String value) {
        StringBuilder topLevel = new StringBuilder(value.length());
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        int braceDepth = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean quoted = singleQuoted || doubleQuoted;
            if (!quoted && current == '{') {
                braceDepth++;
                topLevel.append(' ');
                continue;
            }
            if (!quoted && current == '}') {
                if (braceDepth > 0) braceDepth--;
                topLevel.append(' ');
                continue;
            }
            topLevel.append(braceDepth == 0 || current == '\n' || current == '\r' ? current : ' ');
            if (escaped) {
                escaped = false;
            } else if (quoted && current == '\\') {
                escaped = true;
            } else if (!doubleQuoted && current == '\'') {
                singleQuoted = !singleQuoted;
            } else if (!singleQuoted && current == '"') {
                doubleQuoted = !doubleQuoted;
            }
        }
        return topLevel.toString();
    }

    private static Element directChild(Element parent, String localName) {
        if (parent == null) return null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) return element;
        }
        return null;
    }

    private static Optional<String> childText(Element parent, String localName) {
        Element child = directChild(parent, localName);
        return child == null ? Optional.empty() : Optional.of(child.getTextContent().strip());
    }

    private static List<String> childTexts(Element parent, String localName) {
        if (parent == null) return List.of();
        List<String> values = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                values.add(element.getTextContent().strip());
            }
        }
        return List.copyOf(values);
    }

    private static boolean isDirectory(Path path) {
        return Files.isDirectory(path, NOFOLLOW_LINKS);
    }

    private static boolean isRegularFile(Path path) {
        return Files.isRegularFile(path, NOFOLLOW_LINKS);
    }

    private static DiscoveryDiagnostic diagnostic(
            DiscoveryDiagnosticCode code, Path path, String key, String value) {
        return new DiscoveryDiagnostic(code, path, Map.of(key, value));
    }

    private static final class Candidate {
        private final Path root;
        private final BuildSystem buildSystem;
        private final List<Path> mainOutputs;
        private final List<Path> testOutputs;
        private final List<Path> metadataFiles;
        private final Set<Path> declaredModules;
        private final boolean usable;
        private final boolean ambiguousBuild;

        private Candidate(
                Path root,
                BuildSystem buildSystem,
                List<Path> mainOutputs,
                List<Path> testOutputs,
                List<Path> metadataFiles,
                Set<Path> declaredModules,
                boolean usable,
                boolean ambiguousBuild) {
            this.root = root.toAbsolutePath().normalize();
            this.buildSystem = buildSystem;
            this.mainOutputs = List.copyOf(mainOutputs);
            this.testOutputs = List.copyOf(testOutputs);
            this.metadataFiles = metadataFiles.stream().sorted().toList();
            this.declaredModules = Set.copyOf(declaredModules);
            this.usable = usable;
            this.ambiguousBuild = ambiguousBuild;
        }

        private static Candidate maven(
                Path root, Path pom, List<Path> main, List<Path> test, List<Path> modules) {
            return new Candidate(root, BuildSystem.MAVEN, main, test, List.of(pom),
                    Set.copyOf(modules), true, false);
        }

        private static Candidate gradle(
                Path root,
                List<Path> metadata,
                Set<Path> modules,
                List<Path> main,
                List<Path> test,
                boolean usable) {
            return new Candidate(root, BuildSystem.GRADLE,
                    main, test, metadata, modules, usable, false);
        }

        private static Candidate unusable(Path root, BuildSystem system, List<Path> metadata) {
            return new Candidate(root, system, List.of(), List.of(), metadata, Set.of(), false, false);
        }

        private static Candidate ambiguous(Path root, List<Path> metadata) {
            return new Candidate(root, BuildSystem.EXPLICIT, List.of(), List.of(), metadata,
                    Set.of(), false, true);
        }

        private static Candidate combine(Candidate outer, List<Candidate> projects) {
            List<Path> main = projects.stream().flatMap(project -> project.mainOutputs.stream()).toList();
            List<Path> test = projects.stream().flatMap(project -> project.testOutputs.stream()).toList();
            List<Path> metadata = projects.stream().flatMap(project -> project.metadataFiles.stream()).toList();
            return new Candidate(outer.root, outer.buildSystem, main, test, metadata,
                    outer.declaredModules, true, false);
        }

        private DiscoveredProject toProject() {
            return new DiscoveredProject(root, buildSystem, mainOutputs, testOutputs, metadataFiles);
        }
    }
}
