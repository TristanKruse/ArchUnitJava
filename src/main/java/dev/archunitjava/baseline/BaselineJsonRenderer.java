package dev.archunitjava.baseline;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Canonical JSON rendering for storing a reviewed baseline in version control. */
public final class BaselineJsonRenderer {
    private BaselineJsonRenderer() {}

    public static String render(ReviewedBaseline baseline) {
        ReviewedBaseline value = Objects.requireNonNull(baseline, "baseline");
        StringBuilder out = new StringBuilder("{\"schemaVersion\":\"")
                .append(json(value.schemaVersion())).append("\",\"findings\":[");
        for (int index = 0; index < value.findings().size(); index++) {
            if (index > 0) out.append(',');
            finding(out, value.findings().get(index));
        }
        out.append("],\"suppressions\":[");
        for (int index = 0; index < value.suppressions().size(); index++) {
            if (index > 0) out.append(',');
            suppression(out, value.suppressions().get(index));
        }
        return out.append("]}\n").toString();
    }

    public static byte[] renderBytes(ReviewedBaseline baseline) {
        return render(baseline).getBytes(StandardCharsets.UTF_8);
    }

    private static void finding(StringBuilder out, BaselineFinding finding) {
        out.append('{');
        field(out, "identityFingerprint", finding.identityFingerprint()).append(',');
        field(out, "evidenceFingerprint", finding.evidenceFingerprint()).append(',');
        field(out, "exactFingerprint", finding.exactFingerprint()).append(',');
        field(out, "ruleId", finding.ruleId()).append(',');
        field(out, "violationId", finding.violationId()).append(',');
        field(out, "violationCode", finding.violationCode()).append(',');
        field(out, "severity", finding.severity()).append(',');
        strings(out, "subjects", finding.subjects()).append(',');
        strings(out, "subjectIds", finding.subjectIds()).append(',');
        strings(out, "evidenceLocations", finding.evidenceLocations());
        out.append('}');
    }

    private static void suppression(StringBuilder out, Suppression suppression) {
        out.append('{');
        field(out, "id", suppression.id()).append(',');
        field(out, "rationale", suppression.rationale()).append(',');
        optional(out, "ruleId", suppression.ruleId()).append(',');
        optional(out, "subjectId", suppression.subjectId()).append(',');
        optional(out, "evidenceLocation", suppression.evidenceLocation()).append(',');
        out.append("\"expiresOn\":");
        if (suppression.expiresOn().isPresent()) {
            out.append('"').append(suppression.expiresOn().orElseThrow()).append('"');
        } else out.append("null");
        out.append('}');
    }

    private static StringBuilder field(StringBuilder out, String name, String value) {
        return out.append('"').append(name).append("\":\"").append(json(value)).append('"');
    }

    private static StringBuilder strings(StringBuilder out, String name, Iterable<String> values) {
        out.append('"').append(name).append("\":[");
        int index = 0;
        for (String value : values) {
            if (index++ > 0) out.append(',');
            out.append('"').append(json(value)).append('"');
        }
        return out.append(']');
    }

    private static StringBuilder optional(
            StringBuilder out, String name, java.util.Optional<String> value) {
        out.append('"').append(name).append("\":");
        return value.isPresent()
                ? out.append('"').append(json(value.orElseThrow())).append('"') : out.append("null");
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20 || Character.isSurrogate(character)
                            || character == '<' || character == '>' || character == '&') {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else out.append(character);
                }
            }
        }
        return out.toString();
    }
}
