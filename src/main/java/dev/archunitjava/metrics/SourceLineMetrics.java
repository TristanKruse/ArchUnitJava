package dev.archunitjava.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Physical Java line counts. Mixed code/comment lines are code; whitespace-only lines inside a
 * block comment are comments; text-block content is code. Empty text has zero physical lines.
 */
public record SourceLineMetrics(
        long physicalLines, long blankLines, long commentLines, long codeLines) {
    public SourceLineMetrics {
        if (physicalLines < 0 || blankLines < 0 || commentLines < 0 || codeLines < 0) {
            throw new IllegalArgumentException("line counts must not be negative");
        }
        if (physicalLines != blankLines + commentLines + codeLines) {
            throw new IllegalArgumentException("line categories must partition physical lines");
        }
    }

    public static SourceLineMetrics count(String source) {
        List<String> lines = lines(Objects.requireNonNull(source, "source"));
        LexerState state = new LexerState();
        long blanks = 0;
        long comments = 0;
        long code = 0;
        for (String line : lines) {
            Classification classification = classify(line, state);
            if (classification.code) code++;
            else if (classification.comment) comments++;
            else blanks++;
        }
        return new SourceLineMetrics(lines.size(), blanks, comments, code);
    }

    private static List<String> lines(String source) {
        if (source.isEmpty()) return List.of();
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] split = normalized.split("\n", -1);
        int size = split.length;
        if (normalized.endsWith("\n")) size--;
        List<String> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) result.add(split[index]);
        return List.copyOf(result);
    }

    private static Classification classify(String line, LexerState state) {
        boolean code = state.textBlock;
        boolean comment = state.blockComment;
        for (int offset = 0; offset < line.length();) {
            if (state.blockComment) {
                comment = true;
                int end = line.indexOf("*/", offset);
                if (end < 0) break;
                state.blockComment = false;
                offset = end + 2;
                continue;
            }
            if (state.textBlock) {
                code = true;
                int end = unescaped(line, "\"\"\"", offset);
                if (end < 0) break;
                state.textBlock = false;
                offset = end + 3;
                continue;
            }
            char current = line.charAt(offset);
            if (Character.isWhitespace(current)) {
                offset++;
                continue;
            }
            if (starts(line, offset, "//")) {
                comment = true;
                break;
            }
            if (starts(line, offset, "/*")) {
                comment = true;
                state.blockComment = true;
                offset += 2;
                continue;
            }
            if (starts(line, offset, "\"\"\"")) {
                code = true;
                state.textBlock = true;
                offset += 3;
                continue;
            }
            code = true;
            if (current == '"' || current == '\'') {
                offset = quotedEnd(line, offset + 1, current);
            } else {
                offset++;
            }
        }
        return new Classification(code, comment);
    }

    private static int quotedEnd(String line, int offset, char quote) {
        boolean escaped = false;
        while (offset < line.length()) {
            char current = line.charAt(offset++);
            if (escaped) escaped = false;
            else if (current == '\\') escaped = true;
            else if (current == quote) break;
        }
        return offset;
    }

    private static int unescaped(String value, String token, int from) {
        int candidate = value.indexOf(token, from);
        while (candidate >= 0) {
            int slashes = 0;
            for (int index = candidate - 1; index >= 0 && value.charAt(index) == '\\'; index--) {
                slashes++;
            }
            if (slashes % 2 == 0) return candidate;
            candidate = value.indexOf(token, candidate + 1);
        }
        return -1;
    }

    private static boolean starts(String value, int offset, String token) {
        return value.regionMatches(offset, token, 0, token.length());
    }

    private record Classification(boolean code, boolean comment) {}

    private static final class LexerState {
        private boolean blockComment;
        private boolean textBlock;
    }
}
