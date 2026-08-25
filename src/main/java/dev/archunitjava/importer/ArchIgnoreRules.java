package dev.archunitjava.importer;

import dev.archunitjava.pattern.JavaPattern;
import dev.archunitjava.pattern.PatternDomain;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Strict data-only parser for root-relative .archignore files. */
final class ArchIgnoreRules {
    private ArchIgnoreRules() {}

    static ParseResult parse(byte[] bytes, String source, int maximumLines) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            return new ParseResult(List.of(), List.of(invalid(source, 0, "invalid-utf8", "")));
        }
        String[] lines = text.split("\\R", -1);
        if (lines.length > maximumLines) {
            return new ParseResult(
                    List.of(),
                    List.of(new InputDiagnostic(
                            InputDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                            source,
                            Map.of("limit", "archignore-lines:" + maximumLines))));
        }
        List<ImportResourceRule> rules = new ArrayList<>();
        List<InputDiagnostic> diagnostics = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index].strip();
            if (raw.isEmpty() || raw.startsWith("#")) continue;
            boolean include = raw.startsWith("!");
            String expression = include ? raw.substring(1) : raw;
            String reason = invalidReason(expression);
            if (reason != null) {
                diagnostics.add(invalid(source, index + 1, reason, raw));
                continue;
            }
            try {
                rules.add(new ImportResourceRule(
                        include ? ImportRuleAction.INCLUDE : ImportRuleAction.EXCLUDE,
                        JavaPattern.glob(PatternDomain.RESOURCE_PATH, expression),
                        source,
                        index + 1));
            } catch (RuntimeException failure) {
                diagnostics.add(invalid(source, index + 1, "invalid-glob", raw));
            }
        }
        return new ParseResult(rules, diagnostics);
    }

    private static String invalidReason(String expression) {
        if (expression.isBlank()) return "empty-pattern";
        if (expression.startsWith("/")
                || expression.startsWith("~")
                || expression.matches("^[A-Za-z]:.*")) return "absolute-pattern";
        if (expression.indexOf('\\') >= 0) return "backslash";
        if (expression.indexOf('\0') >= 0) return "nul";
        for (String segment : expression.split("/", -1)) {
            if (segment.equals("..") || segment.isEmpty()) return "outside-root";
        }
        if (expression.chars().anyMatch(value -> value < 0x20)) return "control-character";
        if (expression.indexOf('$') >= 0
                || expression.indexOf('%') >= 0
                || expression.indexOf('`') >= 0
                || expression.indexOf('|') >= 0
                || expression.indexOf(';') >= 0
                || expression.indexOf('&') >= 0
                || expression.indexOf('<') >= 0
                || expression.indexOf('>') >= 0) return "expansion-or-command-token";
        return null;
    }

    private static InputDiagnostic invalid(
            String source, int line, String reason, String rule) {
        return new InputDiagnostic(
                InputDiagnosticCode.INVALID_IGNORE_RULE,
                source,
                Map.of("line", Integer.toString(line), "reason", reason, "rule", rule.isEmpty() ? "-" : rule));
    }

    record ParseResult(List<ImportResourceRule> rules, List<InputDiagnostic> diagnostics) {
        ParseResult {
            rules = List.copyOf(rules);
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
