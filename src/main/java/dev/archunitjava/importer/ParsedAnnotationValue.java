package dev.archunitjava.importer;

import java.util.List;
import java.util.Objects;

/** Backend-neutral, lossless annotation element value. */
public sealed interface ParsedAnnotationValue extends Comparable<ParsedAnnotationValue>
        permits ParsedAnnotationValue.ArrayValue,
                ParsedAnnotationValue.ClassValue,
                ParsedAnnotationValue.EnumValue,
                ParsedAnnotationValue.NestedAnnotationValue,
                ParsedAnnotationValue.ScalarValue {
    String stableKey();

    @Override
    default int compareTo(ParsedAnnotationValue other) {
        return stableKey().compareTo(other.stableKey());
    }

    enum ScalarKind {
        BOOLEAN,
        BYTE,
        CHAR,
        SHORT,
        INT,
        LONG,
        FLOAT_RAW_BITS,
        DOUBLE_RAW_BITS,
        STRING
    }

    record ScalarValue(ScalarKind kind, String encodedValue) implements ParsedAnnotationValue {
        public ScalarValue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(encodedValue, "encodedValue");
        }

        @Override
        public String stableKey() {
            return "scalar:" + kind + ":" + encodedValue.length() + ":" + encodedValue;
        }
    }

    record EnumValue(String typeDescriptor, String constantName) implements ParsedAnnotationValue {
        public EnumValue {
            typeDescriptor = requireText(typeDescriptor, "typeDescriptor");
            constantName = requireText(constantName, "constantName");
        }

        @Override
        public String stableKey() {
            return "enum:" + typeDescriptor + ":" + constantName;
        }
    }

    record ClassValue(String descriptor) implements ParsedAnnotationValue {
        public ClassValue {
            descriptor = requireText(descriptor, "descriptor");
        }

        @Override
        public String stableKey() {
            return "class:" + descriptor;
        }
    }

    record NestedAnnotationValue(ParsedAnnotation annotation) implements ParsedAnnotationValue {
        public NestedAnnotationValue {
            Objects.requireNonNull(annotation, "annotation");
        }

        @Override
        public String stableKey() {
            return "annotation:" + annotation.stableKey();
        }
    }

    record ArrayValue(List<ParsedAnnotationValue> values) implements ParsedAnnotationValue {
        public ArrayValue {
            Objects.requireNonNull(values, "values");
            values = List.copyOf(values);
            values.forEach(value -> Objects.requireNonNull(value, "value"));
        }

        @Override
        public String stableKey() {
            StringBuilder key = new StringBuilder("array:");
            values.forEach(value -> key.append(value.stableKey().length())
                    .append(':').append(value.stableKey()));
            return key.toString();
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
