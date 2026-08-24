package dev.archunitjava.model;

import java.util.List;
import java.util.Objects;

/** Ordered JVM method parameters and return type. */
public record JvmMethodType(List<JvmType> parameterTypes, JvmType returnType) {
    public JvmMethodType {
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        parameterTypes = List.copyOf(parameterTypes);
        for (JvmType parameter : parameterTypes) {
            Objects.requireNonNull(parameter, "parameterType");
            if (parameter instanceof JvmVoidType) {
                throw new IllegalArgumentException("Method parameters cannot be void");
            }
        }
        Objects.requireNonNull(returnType, "returnType");
    }

    public String descriptor() {
        StringBuilder result = new StringBuilder("(");
        parameterTypes.forEach(parameter -> result.append(parameter.descriptor()));
        return result.append(')').append(returnType.descriptor()).toString();
    }
}
