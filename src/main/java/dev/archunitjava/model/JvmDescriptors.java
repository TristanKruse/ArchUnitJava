package dev.archunitjava.model;

import java.util.ArrayList;
import java.util.List;

/** Strict, stateless field and method descriptor parser. */
public final class JvmDescriptors {
    private JvmDescriptors() {}

    public static JvmType parseField(String descriptor) {
        Cursor cursor = new Cursor(descriptor);
        JvmType type = cursor.type(false);
        cursor.requireEnd();
        return type;
    }

    public static JvmMethodType parseMethod(String descriptor) {
        Cursor cursor = new Cursor(descriptor);
        cursor.require('(', "method descriptor must start with '('");
        List<JvmType> parameters = new ArrayList<>();
        while (!cursor.peek(')')) {
            if (cursor.atEnd()) throw cursor.failure("missing ')' after method parameters");
            parameters.add(cursor.type(false));
        }
        cursor.require(')', "missing ')' after method parameters");
        JvmType returnType = cursor.type(true);
        cursor.requireEnd();
        return new JvmMethodType(parameters, returnType);
    }

    private static final class Cursor {
        private final String descriptor;
        private int offset;

        Cursor(String descriptor) {
            if (descriptor == null) throw new NullPointerException("descriptor");
            this.descriptor = descriptor;
        }

        JvmType type(boolean allowVoid) {
            if (atEnd()) throw failure("expected a type");
            char marker = descriptor.charAt(offset++);
            return switch (marker) {
                case 'Z' -> JvmPrimitiveType.BOOLEAN;
                case 'B' -> JvmPrimitiveType.BYTE;
                case 'C' -> JvmPrimitiveType.CHAR;
                case 'S' -> JvmPrimitiveType.SHORT;
                case 'I' -> JvmPrimitiveType.INT;
                case 'J' -> JvmPrimitiveType.LONG;
                case 'F' -> JvmPrimitiveType.FLOAT;
                case 'D' -> JvmPrimitiveType.DOUBLE;
                case 'V' -> {
                    if (!allowVoid) throw failureAt(offset - 1, "void is not legal here");
                    yield JvmVoidType.VOID;
                }
                case 'L' -> reference();
                case '[' -> array();
                default -> throw failureAt(offset - 1, "unknown type marker '" + marker + "'");
            };
        }

        private JvmReferenceType reference() {
            int start = offset;
            int end = descriptor.indexOf(';', start);
            if (end < 0) throw failure("unterminated reference type");
            String internalName = descriptor.substring(start, end);
            if (!validInternalName(internalName)) {
                throw failureAt(start, "invalid reference internal name");
            }
            offset = end + 1;
            return new JvmReferenceType(internalName.replace('/', '.'));
        }

        private JvmArrayType array() {
            int dimensions = 1;
            while (peek('[')) {
                offset++;
                dimensions++;
                if (dimensions > JvmArrayType.MAXIMUM_DIMENSIONS) {
                    throw failureAt(offset - 1, "array has more than 255 dimensions");
                }
            }
            JvmType element = type(false);
            if (element instanceof JvmArrayType) {
                throw failure("array element was not canonical");
            }
            return new JvmArrayType(element, dimensions);
        }

        void requireEnd() {
            if (!atEnd()) throw failure("unexpected trailing descriptor data");
        }

        void require(char expected, String reason) {
            if (atEnd() || descriptor.charAt(offset) != expected) throw failure(reason);
            offset++;
        }

        boolean peek(char expected) {
            return !atEnd() && descriptor.charAt(offset) == expected;
        }

        boolean atEnd() {
            return offset >= descriptor.length();
        }

        InvalidJvmDescriptorException failure(String reason) {
            return failureAt(offset, reason);
        }

        InvalidJvmDescriptorException failureAt(int failureOffset, String reason) {
            return new InvalidJvmDescriptorException(descriptor, failureOffset, reason);
        }

        private static boolean validInternalName(String value) {
            if (value.isEmpty()
                    || value.startsWith("/")
                    || value.endsWith("/")
                    || value.contains("//")) return false;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '.' || character == '[' || character == ';' || character == '\0') {
                    return false;
                }
            }
            return true;
        }
    }
}
