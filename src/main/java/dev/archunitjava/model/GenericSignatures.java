package dev.archunitjava.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Strict, bounded parser for JVMS class, method, and field generic signatures. */
public final class GenericSignatures {
    private GenericSignatures() {}

    public static GenericType parseField(String signature) {
        Cursor cursor = new Cursor(signature);
        GenericType result = cursor.fieldType();
        cursor.requireEnd();
        return result;
    }

    public static GenericClassSignature parseClass(String signature) {
        Cursor cursor = new Cursor(signature);
        List<GenericTypeParameter> parameters = cursor.typeParameters();
        GenericType.ClassType superclass = cursor.classType();
        List<GenericType.ClassType> interfaces = new ArrayList<>();
        while (!cursor.atEnd()) interfaces.add(cursor.classType());
        return new GenericClassSignature(parameters, superclass, interfaces);
    }

    public static GenericMethodSignature parseMethod(String signature) {
        Cursor cursor = new Cursor(signature);
        List<GenericTypeParameter> parameters = cursor.typeParameters();
        cursor.require('(', "Expected method parameters");
        List<GenericType> parameterTypes = new ArrayList<>();
        while (!cursor.atEnd() && cursor.current() != ')') {
            parameterTypes.add(cursor.type(false));
        }
        cursor.require(')', "Unterminated method parameters");
        GenericType returnType = cursor.type(true);
        List<GenericType> throwsTypes = new ArrayList<>();
        while (!cursor.atEnd() && cursor.current() == '^') {
            cursor.advance();
            GenericType thrown = cursor.current() == 'T'
                    ? cursor.typeVariable()
                    : cursor.classType();
            throwsTypes.add(thrown);
        }
        cursor.requireEnd();
        return new GenericMethodSignature(parameters, parameterTypes, returnType, throwsTypes);
    }

    private static final class Cursor {
        private static final int MAXIMUM_NESTING = 256;

        private final String signature;
        private int offset;
        private int nesting;

        private Cursor(String signature) {
            if (signature == null || signature.isBlank()) {
                throw new IllegalArgumentException("signature must not be blank");
            }
            this.signature = signature;
        }

        private GenericType type(boolean allowVoid) {
            enter();
            try {
                if (atEnd()) throw invalid("Expected a type");
                return switch (current()) {
                    case 'B' -> primitive(JvmPrimitiveType.BYTE);
                    case 'C' -> primitive(JvmPrimitiveType.CHAR);
                    case 'D' -> primitive(JvmPrimitiveType.DOUBLE);
                    case 'F' -> primitive(JvmPrimitiveType.FLOAT);
                    case 'I' -> primitive(JvmPrimitiveType.INT);
                    case 'J' -> primitive(JvmPrimitiveType.LONG);
                    case 'S' -> primitive(JvmPrimitiveType.SHORT);
                    case 'Z' -> primitive(JvmPrimitiveType.BOOLEAN);
                    case 'V' -> {
                        if (!allowVoid) throw invalid("Void is not valid here");
                        advance();
                        yield new GenericType.VoidType();
                    }
                    case '[' -> arrayType();
                    case 'T' -> typeVariable();
                    case 'L' -> classType();
                    default -> throw invalid("Unknown type-signature marker '" + current() + "'");
                };
            } finally {
                nesting--;
            }
        }

        private GenericType fieldType() {
            GenericType result = type(false);
            if (result instanceof GenericType.PrimitiveType) {
                throw invalid("A generic field signature must be reference-like");
            }
            return result;
        }

        private GenericType primitive(JvmPrimitiveType primitive) {
            advance();
            return new GenericType.PrimitiveType(primitive);
        }

        private GenericType arrayType() {
            int dimensions = 0;
            while (!atEnd() && current() == '[') {
                dimensions++;
                if (dimensions > JvmArrayType.MAXIMUM_DIMENSIONS) {
                    throw invalid("Array dimensions exceed " + JvmArrayType.MAXIMUM_DIMENSIONS);
                }
                advance();
            }
            GenericType element = type(false);
            return new GenericType.ArrayType(element, dimensions);
        }

        private GenericType.TypeVariable typeVariable() {
            require('T', "Expected a type variable");
            int start = offset;
            while (!atEnd() && current() != ';') {
                if (forbiddenIdentifierCharacter(current())) {
                    throw invalid("Invalid type-variable name");
                }
                advance();
            }
            if (offset == start) throw invalid("Empty type-variable name");
            String name = signature.substring(start, offset);
            require(';', "Unterminated type variable");
            return new GenericType.TypeVariable(name);
        }

        private GenericType.ClassType classType() {
            enter();
            try {
                require('L', "Expected a class type");
                int start = offset;
                while (!atEnd() && current() != '<' && current() != '.' && current() != ';') {
                    if (current() == '[' || current() == ':') {
                        throw invalid("Invalid class-type name");
                    }
                    advance();
                }
                if (offset == start) throw invalid("Empty class-type name");
                String internalName = signature.substring(start, offset);
                validateInternalName(internalName);
                int slash = internalName.lastIndexOf('/');
                String simpleName = internalName.substring(slash + 1);
                String binaryName = internalName.replace('/', '.');
                GenericType.ClassType result = new GenericType.ClassType(
                        Optional.empty(),
                        new JvmReferenceType(binaryName),
                        simpleName,
                        typeArguments());
                while (!atEnd() && current() == '.') {
                    advance();
                    int nestedStart = offset;
                    while (!atEnd() && current() != '<' && current() != '.' && current() != ';') {
                        if (forbiddenIdentifierCharacter(current()) || current() == '/') {
                            throw invalid("Invalid nested class name");
                        }
                        advance();
                    }
                    if (offset == nestedStart) throw invalid("Empty nested class name");
                    String nestedName = signature.substring(nestedStart, offset);
                    result = new GenericType.ClassType(
                            Optional.of(result),
                            new JvmReferenceType(result.rawType().binaryName() + "$" + nestedName),
                            nestedName,
                            typeArguments());
                }
                require(';', "Unterminated class type");
                return result;
            } finally {
                nesting--;
            }
        }

        private List<GenericTypeArgument> typeArguments() {
            if (atEnd() || current() != '<') return List.of();
            advance();
            List<GenericTypeArgument> result = new ArrayList<>();
            while (!atEnd() && current() != '>') {
                result.add(switch (current()) {
                    case '*' -> {
                        advance();
                        yield GenericTypeArgument.unbounded();
                    }
                    case '+' -> {
                        advance();
                        yield GenericTypeArgument.extendsBound(fieldType());
                    }
                    case '-' -> {
                        advance();
                        yield GenericTypeArgument.superBound(fieldType());
                    }
                    default -> GenericTypeArgument.exact(fieldType());
                });
            }
            if (result.isEmpty()) throw invalid("Generic argument list must not be empty");
            require('>', "Unterminated generic argument list");
            return List.copyOf(result);
        }

        private List<GenericTypeParameter> typeParameters() {
            if (atEnd() || current() != '<') return List.of();
            advance();
            List<GenericTypeParameter> result = new ArrayList<>();
            while (!atEnd() && current() != '>') {
                int start = offset;
                while (!atEnd() && current() != ':') {
                    if (forbiddenIdentifierCharacter(current())) {
                        throw invalid("Invalid type-parameter name");
                    }
                    advance();
                }
                if (offset == start) throw invalid("Empty type-parameter name");
                String name = signature.substring(start, offset);
                require(':', "Type parameter has no bound separator");
                Optional<GenericType> classBound = Optional.empty();
                if (!atEnd() && current() != ':') classBound = Optional.of(fieldType());
                List<GenericType> interfaceBounds = new ArrayList<>();
                while (!atEnd() && current() == ':') {
                    advance();
                    interfaceBounds.add(fieldType());
                }
                if (classBound.isEmpty() && interfaceBounds.isEmpty()) {
                    throw invalid("Type parameter must declare a class or interface bound");
                }
                result.add(new GenericTypeParameter(name, classBound, interfaceBounds));
            }
            if (result.isEmpty()) throw invalid("Type-parameter list must not be empty");
            require('>', "Unterminated type-parameter list");
            return List.copyOf(result);
        }

        private void validateInternalName(String name) {
            if (name.startsWith("/") || name.endsWith("/") || name.contains("//")) {
                throw invalid("Invalid class-type internal name");
            }
            for (int index = 0; index < name.length(); index++) {
                if (forbiddenIdentifierCharacter(name.charAt(index)) && name.charAt(index) != '/') {
                    throw invalid("Invalid class-type internal name");
                }
            }
        }

        private boolean forbiddenIdentifierCharacter(char value) {
            return value == '.' || value == ';' || value == '[' || value == '<'
                    || value == '>' || value == ':' || value == '\0';
        }

        private void enter() {
            nesting++;
            if (nesting > MAXIMUM_NESTING) {
                throw invalid("Generic signature nesting exceeds " + MAXIMUM_NESTING);
            }
        }

        private char current() {
            if (atEnd()) throw invalid("Unexpected end of signature");
            return signature.charAt(offset);
        }

        private void advance() {
            offset++;
        }

        private void require(char expected, String message) {
            if (atEnd() || current() != expected) throw invalid(message);
            advance();
        }

        private void requireEnd() {
            if (!atEnd()) throw invalid("Unexpected trailing generic-signature content");
        }

        private boolean atEnd() {
            return offset >= signature.length();
        }

        private InvalidGenericSignatureException invalid(String reason) {
            return new InvalidGenericSignatureException(signature, offset, reason);
        }
    }
}
