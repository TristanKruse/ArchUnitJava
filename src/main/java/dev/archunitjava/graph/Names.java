package dev.archunitjava.graph;

import javax.lang.model.SourceVersion;

final class Names {
    private Names() {}

    static String qualifiedJavaName(String value, String description) {
        if (value == null || value.isBlank() || value.startsWith(".") || value.endsWith(".")) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value);
        }
        for (String part : value.split("\\.", -1)) {
            if (!javaIdentifier(part)) {
                throw new IllegalArgumentException("Invalid " + description + ": " + value);
            }
        }
        return value;
    }

    static String binaryName(String value) {
        if (value == null || value.isBlank() || value.startsWith(".") || value.endsWith(".")
                || value.indexOf('/') >= 0 || value.indexOf('[') >= 0 || value.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Invalid binary name: " + value);
        }
        for (String part : value.split("\\.", -1)) {
            if (!binaryNamePart(part)) {
                throw new IllegalArgumentException("Invalid binary name: " + value);
            }
        }
        return value;
    }

    private static boolean javaIdentifier(String value) {
        if (value.isEmpty() || SourceVersion.isKeyword(value)
                || !Character.isJavaIdentifierStart(value.codePointAt(0))) return false;
        for (int offset = Character.charCount(value.codePointAt(0)); offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isJavaIdentifierPart(codePoint)) return false;
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean binaryNamePart(String value) {
        return javaIdentifier(value);
    }
}
