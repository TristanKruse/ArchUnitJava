package dev.archunitjava.graph;

/** Validation only; descriptor interpretation belongs to the descriptor vocabulary. */
final class DescriptorGrammar {
    private DescriptorGrammar() {}

    static boolean isFieldDescriptor(String value) {
        if (value == null || value.isEmpty()) return false;
        Cursor cursor = new Cursor(value);
        return fieldType(cursor, 0) && cursor.atEnd();
    }

    static boolean isMethodDescriptor(String value) {
        if (value == null || value.length() < 3 || value.charAt(0) != '(') return false;
        Cursor cursor = new Cursor(value);
        cursor.next();
        int slots = 0;
        while (!cursor.atEnd() && cursor.peek() != ')') {
            char first = cursor.peek();
            if (!fieldType(cursor, 0)) return false;
            slots += first == 'J' || first == 'D' ? 2 : 1;
            if (slots > 255) return false;
        }
        if (cursor.atEnd() || cursor.next() != ')' || cursor.atEnd()) return false;
        if (cursor.peek() == 'V') cursor.next();
        else if (!fieldType(cursor, 0)) return false;
        return cursor.atEnd();
    }

    private static boolean fieldType(Cursor cursor, int dimensions) {
        if (cursor.atEnd()) return false;
        char kind = cursor.next();
        if ("BCDFIJSZ".indexOf(kind) >= 0) return true;
        if (kind == '[') return dimensions < 255 && fieldType(cursor, dimensions + 1);
        if (kind != 'L') return false;
        boolean hasCharacter = false;
        boolean segmentHasCharacter = false;
        while (!cursor.atEnd() && cursor.peek() != ';') {
            char c = cursor.next();
            if (c == '.' || c == '[') return false;
            if (c == '/') {
                if (!segmentHasCharacter) return false;
                segmentHasCharacter = false;
            } else {
                segmentHasCharacter = true;
                hasCharacter = true;
            }
        }
        return !cursor.atEnd() && cursor.next() == ';' && hasCharacter && segmentHasCharacter;
    }

    private static final class Cursor {
        private final String value;
        private int position;
        Cursor(String value) { this.value = value; }
        boolean atEnd() { return position == value.length(); }
        char peek() { return value.charAt(position); }
        char next() { return value.charAt(position++); }
    }
}
