package dev.archunitjava.report;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.result.Diagnostic;
import dev.archunitjava.result.Violation;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class ResultRenderSupport {
    private ResultRenderSupport() {}

    static StringBuilder jsonField(StringBuilder out, String name, String value) {
        return out.append('"').append(name).append("\":\"")
                .append(ReportEscapes.json(value)).append('"');
    }

    static StringBuilder jsonOptionalField(
            StringBuilder out, String name, java.util.Optional<String> value) {
        out.append('"').append(name).append("\":");
        return value.isPresent()
                ? out.append('"').append(ReportEscapes.json(value.orElseThrow())).append('"')
                : out.append("null");
    }

    static void jsonStringArray(StringBuilder out, Iterable<String> values) {
        out.append('[');
        int index = 0;
        for (String value : values) {
            if (index++ > 0) out.append(',');
            out.append('"').append(ReportEscapes.json(value)).append('"');
        }
        out.append(']');
    }

    static void jsonMap(StringBuilder out, Map<String, String> values) {
        out.append('{');
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (index++ > 0) out.append(',');
            jsonField(out, entry.getKey(), entry.getValue());
        }
        out.append('}');
    }

    static void jsonDiagnostic(StringBuilder out, Diagnostic diagnostic) {
        out.append('{');
        jsonField(out, "code", diagnostic.code()).append(',');
        jsonField(out, "severity", diagnostic.severity().name()).append(',');
        out.append("\"context\":");
        jsonMap(out, diagnostic.context());
        out.append('}');
    }

    static void jsonViolation(StringBuilder out, Violation violation) {
        out.append('{');
        jsonField(out, "id", violation.id().value()).append(',');
        jsonField(out, "code", violation.code()).append(',');
        jsonField(out, "severity", violation.severity().name()).append(',');
        out.append("\"subjects\":[");
        for (int index = 0; index < violation.subjects().size(); index++) {
            if (index > 0) out.append(',');
            var subject = violation.subjects().get(index);
            out.append('{');
            jsonField(out, "role", subject.role()).append(',');
            jsonField(out, "id", subject.id().stableKey());
            out.append('}');
        }
        out.append("],\"evidence\":[");
        for (int index = 0; index < violation.evidence().size(); index++) {
            if (index > 0) out.append(',');
            jsonEvidence(out, violation.evidence().get(index));
        }
        out.append("],\"attributes\":");
        jsonMap(out, violation.attributes());
        out.append('}');
    }

    static void jsonEvidence(StringBuilder out, DependencyEvidence evidence) {
        out.append('{');
        jsonField(out, "location", evidence.location().resourcePath()).append(',');
        out.append("\"ownerMember\":");
        if (evidence.ownerMember().isPresent()) {
            out.append('"').append(ReportEscapes.json(
                    evidence.ownerMember().orElseThrow().stableKey())).append('"');
        } else {
            out.append("null");
        }
        out.append(",\"bytecodeOffset\":");
        if (evidence.bytecodeOffset().isPresent()) {
            out.append(evidence.bytecodeOffset().getAsInt());
        } else {
            out.append("null");
        }
        out.append(",\"sourceFile\":");
        if (evidence.sourceFile().isPresent()) {
            out.append('"').append(ReportEscapes.json(evidence.sourceFile().orElseThrow())).append('"');
        } else {
            out.append("null");
        }
        out.append(",\"lineNumber\":");
        if (evidence.lineNumber().isPresent()) {
            out.append(evidence.lineNumber().getAsInt());
        } else {
            out.append("null");
        }
        out.append('}');
    }

    static String xml(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (isXmlCodePoint(codePoint)) out.appendCodePoint(codePoint);
                    else out.append("&#xfffd;");
                }
            }
        });
        return out.toString();
    }

    static String repositoryUri(String resourcePath) {
        if (resourcePath.startsWith("/") || resourcePath.contains("../")
                || resourcePath.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("SARIF location must be repository-relative");
        }
        StringBuilder out = new StringBuilder(resourcePath.length());
        for (byte value : resourcePath.getBytes(StandardCharsets.UTF_8)) {
            int octet = Byte.toUnsignedInt(value);
            if (octet >= 'a' && octet <= 'z' || octet >= 'A' && octet <= 'Z'
                    || octet >= '0' && octet <= '9' || octet == '-' || octet == '.'
                    || octet == '_' || octet == '~' || octet == '/') {
                out.append((char) octet);
            } else {
                String hex = "0123456789ABCDEF";
                out.append('%').append(hex.charAt(octet >>> 4)).append(hex.charAt(octet & 0xf));
            }
        }
        return out.toString();
    }

    private static boolean isXmlCodePoint(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF
                || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }
}
