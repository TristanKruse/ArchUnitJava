package dev.archunitjava.report;

/** Central target-string escaping for report formats. */
final class ReportEscapes {
    private ReportEscapes() {}

    static String dot(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> appendControlEscaped(result, character);
            }
        }
        return result.toString();
    }

    static String mermaid(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> result.append("&amp;");
                case '"' -> result.append("&quot;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '\'' -> result.append("&#39;");
                case '[' -> result.append("&#91;");
                case ']' -> result.append("&#93;");
                case '{' -> result.append("&#123;");
                case '}' -> result.append("&#125;");
                case '|' -> result.append("&#124;");
                case '`' -> result.append("&#96;");
                case '%' -> result.append("&#37;");
                case '\\' -> result.append("&#92;");
                case '\n' -> result.append("&#10;");
                case '\r' -> result.append("&#13;");
                case '\t' -> result.append("&#9;");
                default -> {
                    if (Character.isISOControl(character)) {
                        result.append("&#").append((int) character).append(';');
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }

    static String json(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20
                            || Character.isSurrogate(character)
                            || character == '\u2028'
                            || character == '\u2029'
                            || character == '<'
                            || character == '>'
                            || character == '&') {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }

    static String d2(String value) {
        return json(value);
    }

    static String html(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&#39;");
                case '\n' -> result.append("&#10;");
                case '\r' -> result.append("&#13;");
                case '\t' -> result.append("&#9;");
                default -> {
                    if (Character.isISOControl(character)) {
                        result.append("&#").append((int) character).append(';');
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }

    private static void appendControlEscaped(StringBuilder result, char character) {
        if (Character.isISOControl(character)) {
            result.append(String.format("\\u%04x", (int) character));
        } else {
            result.append(character);
        }
    }
}
