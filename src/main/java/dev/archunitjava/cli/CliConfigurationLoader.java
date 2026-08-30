package dev.archunitjava.cli;

import dev.archunitjava.execution.CheckOptions;
import dev.archunitjava.execution.EmptySelectionPolicy;
import dev.archunitjava.result.Severity;
import dev.archunitjava.rules.DependencyRuleMode;
import dev.archunitjava.rules.ExternalDependencyPolicy;
import dev.archunitjava.rules.SelfDependencyPolicy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Strict bounded parser for the non-executable ArchUnitJava properties format. */
public final class CliConfigurationLoader {
    private static final int MAXIMUM_BYTES = 65_536;
    private static final int MAXIMUM_LINES = 1_024;
    private static final int MAXIMUM_INPUTS = 64;
    private static final int MAXIMUM_RULES = 256;
    private static final Set<String> ROOT_KEYS = Set.of(
            "schema", "inputs", "rules", "emptySelection", "allowIncompleteAnalysis",
            "resultFormat", "graphFormat", "graphDomain");
    private static final Set<String> RULE_FIELDS = Set.of(
            "domain", "mode", "origins", "targets", "self", "external",
            "displayName", "rationale", "tags", "severity");

    private CliConfigurationLoader() {}

    public static CliConfiguration load(Path configuration, Path approvedRoot) {
        try {
            Path root = requireRoot(approvedRoot);
            Path source = approvedPath(root, configuration, "configuration file", true);
            Map<String, String> properties = parse(readBounded(source));
            if (!CliConfiguration.SCHEMA.equals(required(properties, "schema"))) {
                throw new CliConfigurationException("Unsupported configuration schema");
            }
            List<String> inputValues = list(required(properties, "inputs"), "inputs", MAXIMUM_INPUTS);
            List<Path> inputs = inputValues.stream()
                    .map(value -> approvedPath(root, Path.of(value), "input", true)).toList();
            List<String> ruleIds = list(required(properties, "rules"), "rules", MAXIMUM_RULES);
            List<CliRuleConfiguration> rules = ruleIds.stream()
                    .map(id -> rule(properties, id)).toList();
            validateKeys(properties.keySet(), new HashSet<>(ruleIds));
            CheckOptions options = CheckOptions.builder()
                    .emptySelectionPolicy(emptyPolicy(properties.getOrDefault(
                            "emptySelection", "fail")))
                    .allowIncompleteAnalysis(bool(properties.getOrDefault(
                            "allowIncompleteAnalysis", "false"), "allowIncompleteAnalysis"))
                    .build();
            return new CliConfiguration(
                    root,
                    source,
                    inputs,
                    rules,
                    options,
                    CliResultFormat.parse(properties.getOrDefault("resultFormat", "console")),
                    CliGraphFormat.parse(properties.getOrDefault("graphFormat", "dot")),
                    CliGraphDomain.parse(properties.getOrDefault("graphDomain", "types")));
        } catch (CliConfigurationException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new CliConfigurationException("Could not load configuration: " + error.getMessage(), error);
        }
    }

    private static CliRuleConfiguration rule(Map<String, String> values, String id) {
        if (!id.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new CliConfigurationException("Invalid rule id: " + id);
        }
        String prefix = "rule." + id + '.';
        CliGraphDomain domain = CliGraphDomain.parse(required(values, prefix + "domain"));
        DependencyRuleMode mode = enumValue(
                DependencyRuleMode.class, required(values, prefix + "mode"), "rule mode");
        SelfDependencyPolicy self = enumValue(
                SelfDependencyPolicy.class, values.getOrDefault(prefix + "self", "ignore"),
                "self-dependency policy");
        ExternalDependencyPolicy external = switch (values.getOrDefault(
                prefix + "external", "fail").toLowerCase(java.util.Locale.ROOT)) {
            case "ignore" -> ExternalDependencyPolicy.IGNORE;
            case "fail" -> ExternalDependencyPolicy.FAIL;
            case "non-matching", "treat-as-non-matching" ->
                    ExternalDependencyPolicy.TREAT_AS_NON_MATCHING;
            default -> throw new CliConfigurationException(
                    "Unsupported external-dependency policy for rule " + id);
        };
        List<String> tags = values.containsKey(prefix + "tags")
                ? list(values.get(prefix + "tags"), prefix + "tags", 64) : List.of();
        return new CliRuleConfiguration(
                id,
                domain,
                mode,
                CliPattern.parse(required(values, prefix + "origins")),
                CliPattern.parse(required(values, prefix + "targets")),
                self,
                external,
                Optional.ofNullable(values.get(prefix + "displayName")),
                Optional.ofNullable(values.get(prefix + "rationale")),
                tags,
                enumValue(Severity.class, values.getOrDefault(prefix + "severity", "error"),
                        "severity"));
    }

    private static Map<String, String> parse(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw new CliConfigurationException("Configuration is not valid UTF-8", error);
        }
        String[] lines = text.split("\\R", -1);
        if (lines.length > MAXIMUM_LINES) {
            throw new CliConfigurationException("Configuration exceeds 1024 lines");
        }
        TreeMap<String, String> result = new TreeMap<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator < 1) {
                throw new CliConfigurationException("Invalid configuration line " + (index + 1));
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.matches("[A-Za-z0-9._-]{1,128}") || value.length() > 4096
                    || value.codePoints().anyMatch(Character::isISOControl)) {
                throw new CliConfigurationException("Invalid key or value at line " + (index + 1));
            }
            if (result.putIfAbsent(key, value) != null) {
                throw new CliConfigurationException("Duplicate configuration key: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static byte[] readBounded(Path source) throws IOException {
        if (Files.size(source) > MAXIMUM_BYTES) {
            throw new CliConfigurationException("Configuration exceeds 65536 bytes");
        }
        try (var input = Files.newInputStream(
                source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAXIMUM_BYTES + 1);
            if (bytes.length > MAXIMUM_BYTES || input.read() != -1) {
                throw new CliConfigurationException("Configuration exceeds 65536 bytes");
            }
            return bytes;
        }
    }

    private static void validateKeys(Set<String> keys, Set<String> ruleIds) {
        for (String key : keys) {
            if (ROOT_KEYS.contains(key)) continue;
            if (!key.startsWith("rule.")) {
                throw new CliConfigurationException("Unknown configuration key: " + key);
            }
            String remainder = key.substring("rule.".length());
            int separator = remainder.indexOf('.');
            if (separator < 1 || !ruleIds.contains(remainder.substring(0, separator))
                    || !RULE_FIELDS.contains(remainder.substring(separator + 1))) {
                throw new CliConfigurationException("Unknown rule configuration key: " + key);
            }
        }
    }

    private static Path requireRoot(Path value) throws IOException {
        if (value == null) throw new CliConfigurationException("Approved root is required");
        Path root = value.toRealPath();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new CliConfigurationException("Approved root must be a directory");
        }
        return root;
    }

    private static Path approvedPath(
            Path root, Path value, String role, boolean mustExist) {
        try {
            Path candidate = (value.isAbsolute() ? value : root.resolve(value)).normalize();
            if (!mustExist && !candidate.startsWith(root)) {
                throw new CliConfigurationException(role + " escapes the approved root");
            }
            Path resolved = mustExist ? candidate.toRealPath() : candidate.toAbsolutePath();
            if (!resolved.startsWith(root)) {
                throw new CliConfigurationException(role + " resolves outside the approved root");
            }
            return resolved;
        } catch (IOException error) {
            throw new CliConfigurationException(role + " does not exist or is unreadable", error);
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new CliConfigurationException("Missing configuration key: " + key);
        }
        return value;
    }

    private static List<String> list(String value, String role, int maximum) {
        if (value == null || value.isBlank()) {
            throw new CliConfigurationException(role + " must not be empty");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String item : value.split(",", -1)) {
            String normalized = item.trim();
            if (normalized.isEmpty() || !seen.add(normalized)) {
                throw new CliConfigurationException(role + " contains an empty or duplicate item");
            }
            result.add(normalized);
        }
        if (result.size() > maximum) {
            throw new CliConfigurationException(role + " exceeds its item limit of " + maximum);
        }
        return List.copyOf(result);
    }

    private static boolean bool(String value, String role) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new CliConfigurationException(role + " must be true or false");
        };
    }

    private static EmptySelectionPolicy emptyPolicy(String value) {
        return enumValue(EmptySelectionPolicy.class, value, "empty-selection policy");
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String role) {
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException error) {
            throw new CliConfigurationException("Unsupported " + role + ": " + value, error);
        }
    }
}
