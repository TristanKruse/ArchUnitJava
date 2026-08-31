package dev.archunitjava.baseline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Bounded, dependency-free reader for the canonical reviewed-baseline JSON schema. */
public final class BaselineJsonReader {
    private BaselineJsonReader() {}

    /** Reads strict baseline JSON text with default resource limits. */
    public static ReviewedBaseline read(String json) {
        return read(json, BaselineReadLimits.defaults());
    }

    /** Reads strict baseline JSON text with caller-supplied resource limits. */
    public static ReviewedBaseline read(String json, BaselineReadLimits limits) {
        String value = Objects.requireNonNull(json, "json");
        BaselineReadLimits bounds = Objects.requireNonNull(limits, "limits");
        if (value.length() > bounds.maxBytes()) throw limit("baseline bytes", bounds.maxBytes());
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > bounds.maxBytes()) throw limit("baseline bytes", bounds.maxBytes());
        return parse(value, bounds);
    }

    /** Decodes and reads strict UTF-8 baseline bytes with default resource limits. */
    public static ReviewedBaseline read(byte[] json) {
        return read(json, BaselineReadLimits.defaults());
    }

    /** Decodes and reads strict UTF-8 baseline bytes with caller-supplied resource limits. */
    public static ReviewedBaseline read(byte[] json, BaselineReadLimits limits) {
        byte[] value = Objects.requireNonNull(json, "json");
        BaselineReadLimits bounds = Objects.requireNonNull(limits, "limits");
        if (value.length > bounds.maxBytes()) throw limit("baseline bytes", bounds.maxBytes());
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
            return parse(decoded, bounds);
        } catch (CharacterCodingException invalidUtf8) {
            throw new BaselineFormatException("Baseline is not valid UTF-8", invalidUtf8);
        }
    }

    /** Reads a regular, non-symbolic baseline file with default resource limits. */
    public static ReviewedBaseline read(Path path) throws IOException {
        return read(path, BaselineReadLimits.defaults());
    }

    /** Reads a regular, non-symbolic baseline file with caller-supplied resource limits. */
    public static ReviewedBaseline read(Path path, BaselineReadLimits limits) throws IOException {
        Path value = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        BaselineReadLimits bounds = Objects.requireNonNull(limits, "limits");
        BasicFileAttributes attributes = Files.readAttributes(
                value, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new BaselineFormatException("Baseline path must be a regular non-symbolic file");
        }
        if (attributes.size() > bounds.maxBytes()) {
            throw limit("baseline bytes", bounds.maxBytes());
        }
        try (InputStream input = Files.newInputStream(
                value, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            return read(readBounded(input, bounds.maxBytes()), bounds);
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        for (int count; (count = input.read(buffer)) >= 0;) {
            if (count == 0) continue;
            if (total > maxBytes - count) throw limit("baseline bytes", maxBytes);
            result.write(buffer, 0, count);
            total += count;
        }
        return result.toByteArray();
    }

    private static ReviewedBaseline parse(String json, BaselineReadLimits limits) {
        try {
            return new Parser(json, limits).baseline();
        } catch (BaselineFormatException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new BaselineFormatException("Baseline contains an invalid domain value", error);
        }
    }

    private static BaselineFormatException limit(String resource, int maximum) {
        return new BaselineFormatException(resource + " exceed configured maximum " + maximum);
    }

    private static final class Parser {
        private final String input;
        private final BaselineReadLimits limits;
        private int offset;
        private int depth;

        private Parser(String input, BaselineReadLimits limits) {
            this.input = input;
            this.limits = limits;
        }

        private ReviewedBaseline baseline() {
            enter('{');
            Set<String> fields = new HashSet<>();
            String schemaVersion = null;
            List<BaselineFinding> findings = null;
            List<Suppression> suppressions = null;
            if (!consume('}')) {
                do {
                    String name = fieldName(fields);
                    switch (name) {
                        case "schemaVersion" -> schemaVersion = string();
                        case "findings" -> findings = findings();
                        case "suppressions" -> suppressions = suppressions();
                        default -> throw error("Unknown baseline field");
                    }
                } while (consume(','));
                expect('}');
            }
            leave();
            end();
            if (schemaVersion == null || findings == null || suppressions == null) {
                throw error("Baseline is missing a required field");
            }
            return new ReviewedBaseline(schemaVersion, findings, suppressions);
        }

        private List<BaselineFinding> findings() {
            enter('[');
            List<BaselineFinding> values = new ArrayList<>();
            if (!consume(']')) {
                do {
                    if (values.size() >= limits.maxFindings()) {
                        throw limit("baseline findings", limits.maxFindings());
                    }
                    values.add(finding());
                } while (consume(','));
                expect(']');
            }
            leave();
            return List.copyOf(values);
        }

        private BaselineFinding finding() {
            enter('{');
            Set<String> fields = new HashSet<>();
            FindingFields value = new FindingFields();
            if (!consume('}')) {
                do {
                    switch (fieldName(fields)) {
                        case "identityFingerprint" -> value.identityFingerprint = string();
                        case "evidenceFingerprint" -> value.evidenceFingerprint = string();
                        case "exactFingerprint" -> value.exactFingerprint = string();
                        case "ruleId" -> value.ruleId = string();
                        case "violationId" -> value.violationId = string();
                        case "violationCode" -> value.violationCode = string();
                        case "severity" -> value.severity = string();
                        case "subjects" -> value.subjects = strings();
                        case "subjectIds" -> value.subjectIds = strings();
                        case "evidenceLocations" -> value.evidenceLocations = strings();
                        default -> throw error("Unknown finding field");
                    }
                } while (consume(','));
                expect('}');
            }
            leave();
            value.required(fields);
            return value.build();
        }

        private List<Suppression> suppressions() {
            enter('[');
            List<Suppression> values = new ArrayList<>();
            if (!consume(']')) {
                do {
                    if (values.size() >= limits.maxSuppressions()) {
                        throw limit("baseline suppressions", limits.maxSuppressions());
                    }
                    values.add(suppression());
                } while (consume(','));
                expect(']');
            }
            leave();
            return List.copyOf(values);
        }

        private Suppression suppression() {
            enter('{');
            Set<String> fields = new HashSet<>();
            SuppressionFields value = new SuppressionFields();
            if (!consume('}')) {
                do {
                    switch (fieldName(fields)) {
                        case "id" -> value.id = string();
                        case "rationale" -> value.rationale = string();
                        case "ruleId" -> value.ruleId = optionalString();
                        case "subjectId" -> value.subjectId = optionalString();
                        case "evidenceLocation" -> value.evidenceLocation = optionalString();
                        case "expiresOn" -> value.expiresOn = optionalDate();
                        default -> throw error("Unknown suppression field");
                    }
                } while (consume(','));
                expect('}');
            }
            leave();
            value.required(fields);
            return value.build();
        }

        private List<String> strings() {
            enter('[');
            List<String> values = new ArrayList<>();
            if (!consume(']')) {
                do {
                    if (values.size() >= limits.maxValuesPerArray()) {
                        throw limit("baseline array values", limits.maxValuesPerArray());
                    }
                    values.add(string());
                } while (consume(','));
                expect(']');
            }
            leave();
            return List.copyOf(values);
        }

        private Optional<String> optionalString() {
            return nullValue() ? Optional.empty() : Optional.of(string());
        }

        private Optional<LocalDate> optionalDate() {
            if (nullValue()) return Optional.empty();
            try {
                return Optional.of(LocalDate.parse(string()));
            } catch (DateTimeParseException invalidDate) {
                throw new BaselineFormatException("Invalid suppression expiry date", invalidDate);
            }
        }

        private String fieldName(Set<String> fields) {
            String name = string();
            if (!fields.add(name)) throw error("Duplicate object field");
            expect(':');
            return name;
        }

        private String string() {
            whitespace();
            if (offset >= input.length() || input.charAt(offset++) != '"') {
                throw error("Expected JSON string");
            }
            StringBuilder result = new StringBuilder();
            while (offset < input.length()) {
                char character = input.charAt(offset++);
                if (character == '"') return result.toString();
                if (character < 0x20) throw error("Unescaped control character in string");
                if (character == '\\') {
                    if (offset >= input.length()) throw error("Incomplete string escape");
                    char escaped = input.charAt(offset++);
                    switch (escaped) {
                        case '"', '\\', '/' -> append(result, escaped);
                        case 'b' -> append(result, '\b');
                        case 'f' -> append(result, '\f');
                        case 'n' -> append(result, '\n');
                        case 'r' -> append(result, '\r');
                        case 't' -> append(result, '\t');
                        case 'u' -> unicodeEscape(result);
                        default -> throw error("Invalid string escape");
                    }
                } else if (Character.isHighSurrogate(character)) {
                    if (offset >= input.length() || !Character.isLowSurrogate(input.charAt(offset))) {
                        throw error("Unpaired Unicode surrogate");
                    }
                    append(result, character);
                    append(result, input.charAt(offset++));
                } else if (Character.isLowSurrogate(character)) {
                    throw error("Unpaired Unicode surrogate");
                } else {
                    append(result, character);
                }
            }
            throw error("Unterminated JSON string");
        }

        private void unicodeEscape(StringBuilder result) {
            char first = hexCodeUnit();
            if (Character.isHighSurrogate(first)) {
                if (offset + 1 >= input.length() || input.charAt(offset) != '\\'
                        || input.charAt(offset + 1) != 'u') {
                    throw error("Unpaired Unicode surrogate escape");
                }
                offset += 2;
                char second = hexCodeUnit();
                if (!Character.isLowSurrogate(second)) {
                    throw error("Unpaired Unicode surrogate escape");
                }
                append(result, first);
                append(result, second);
            } else if (Character.isLowSurrogate(first)) {
                throw error("Unpaired Unicode surrogate escape");
            } else {
                append(result, first);
            }
        }

        private char hexCodeUnit() {
            if (offset + 4 > input.length()) throw error("Incomplete Unicode escape");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(input.charAt(offset++), 16);
                if (digit < 0) throw error("Invalid Unicode escape");
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private void append(StringBuilder result, char value) {
            if (result.length() >= limits.maxStringCharacters()) {
                throw limit("baseline string characters", limits.maxStringCharacters());
            }
            result.append(value);
        }

        private boolean nullValue() {
            whitespace();
            if (input.startsWith("null", offset)) {
                offset += 4;
                return true;
            }
            return false;
        }

        private void enter(char character) {
            expect(character);
            if (++depth > limits.maxDepth()) throw limit("baseline nesting depth", limits.maxDepth());
        }

        private void leave() {
            depth--;
        }

        private boolean consume(char character) {
            whitespace();
            if (offset < input.length() && input.charAt(offset) == character) {
                offset++;
                return true;
            }
            return false;
        }

        private void expect(char character) {
            if (!consume(character)) throw error("Expected '" + character + "'");
        }

        private void whitespace() {
            while (offset < input.length()) {
                char character = input.charAt(offset);
                if (character != ' ' && character != '\t' && character != '\r' && character != '\n') return;
                offset++;
            }
        }

        private void end() {
            whitespace();
            if (offset != input.length()) throw error("Trailing content after baseline");
        }

        private BaselineFormatException error(String message) {
            return new BaselineFormatException(message + " at character " + offset);
        }
    }

    private static final class FindingFields {
        private static final Set<String> REQUIRED = Set.of(
                "identityFingerprint", "evidenceFingerprint", "exactFingerprint", "ruleId",
                "violationId", "violationCode", "severity", "subjects", "subjectIds",
                "evidenceLocations");
        private String identityFingerprint;
        private String evidenceFingerprint;
        private String exactFingerprint;
        private String ruleId;
        private String violationId;
        private String violationCode;
        private String severity;
        private List<String> subjects;
        private List<String> subjectIds;
        private List<String> evidenceLocations;

        private void required(Set<String> fields) {
            if (!fields.equals(REQUIRED)) {
                throw new BaselineFormatException("Finding is missing a required field");
            }
        }

        private BaselineFinding build() {
            return new BaselineFinding(identityFingerprint, evidenceFingerprint, exactFingerprint,
                    ruleId, violationId, violationCode, severity, subjects, subjectIds,
                    evidenceLocations);
        }
    }

    private static final class SuppressionFields {
        private static final Set<String> REQUIRED = Set.of(
                "id", "rationale", "ruleId", "subjectId", "evidenceLocation", "expiresOn");
        private String id;
        private String rationale;
        private Optional<String> ruleId;
        private Optional<String> subjectId;
        private Optional<String> evidenceLocation;
        private Optional<LocalDate> expiresOn;

        private void required(Set<String> fields) {
            if (!fields.equals(REQUIRED)) {
                throw new BaselineFormatException("Suppression is missing a required field");
            }
        }

        private Suppression build() {
            return new Suppression(id, rationale, ruleId, subjectId, evidenceLocation, expiresOn);
        }
    }
}
