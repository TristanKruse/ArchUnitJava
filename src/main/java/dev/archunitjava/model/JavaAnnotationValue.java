package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Lossless annotation value vocabulary independent from the JDK parser API. */
public sealed interface JavaAnnotationValue extends Comparable<JavaAnnotationValue>
        permits JavaAnnotationValue.ArrayValue,
                JavaAnnotationValue.ClassValue,
                JavaAnnotationValue.EnumValue,
                JavaAnnotationValue.NestedAnnotationValue,
                JavaAnnotationValue.ScalarValue {
    String stableKey();

    @Override
    default int compareTo(JavaAnnotationValue other) {
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

    record ScalarValue(ScalarKind kind, String encodedValue) implements JavaAnnotationValue {
        public ScalarValue {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(encodedValue, "encodedValue");
        }

        @Override
        public String stableKey() {
            return "scalar:" + kind + ":" + encodedValue.length() + ":" + encodedValue;
        }
    }

    record EnumValue(JvmReferenceType enumType, String constantName)
            implements JavaAnnotationValue {
        public EnumValue {
            Objects.requireNonNull(enumType, "enumType");
            if (constantName == null || constantName.isBlank()) {
                throw new IllegalArgumentException("constantName must not be blank");
            }
        }

        @Override
        public String stableKey() {
            return "enum:" + enumType.descriptor() + ":" + constantName;
        }
    }

    record ClassValue(String descriptor) implements JavaAnnotationValue {
        public ClassValue {
            if (descriptor == null || descriptor.isBlank()) {
                throw new IllegalArgumentException("descriptor must not be blank");
            }
        }

        @Override
        public String stableKey() {
            return "class:" + descriptor;
        }
    }

    record NestedAnnotationValue(JavaAnnotation annotation) implements JavaAnnotationValue {
        public NestedAnnotationValue {
            Objects.requireNonNull(annotation, "annotation");
        }

        @Override
        public String stableKey() {
            return "annotation:" + annotation.stableKey();
        }
    }

    record ArrayValue(List<JavaAnnotationValue> values) implements JavaAnnotationValue {
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
}
