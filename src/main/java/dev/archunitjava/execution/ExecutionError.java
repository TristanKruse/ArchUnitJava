package dev.archunitjava.execution;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Base class for execution errors, carrying a stable code and deterministic context. */
public abstract sealed class ExecutionError extends RuntimeException
        permits TechnicalError, UserError {
    private final String code;
    private final Map<String, String> context;

    protected ExecutionError(
            String code, String message, Throwable cause, Map<String, String> context) {
        this(validate(code, message, context), cause);
    }

    private ExecutionError(Validated values, Throwable cause) {
        super(values.message(), cause);
        this.code = values.code();
        this.context = values.context();
    }

    public final String code() {
        return code;
    }

    /** Returns context sorted by key so diagnostics never depend on map insertion order. */
    public final Map<String, String> context() {
        return context;
    }

    private static Map<String, String> immutableContext(Map<String, String> values) {
        Objects.requireNonNull(values, "context");
        TreeMap<String, String> sorted = new TreeMap<>();
        values.forEach((key, value) -> sorted.put(
                requireText(key, "context key"), requireText(value, "context value")));
        return Collections.unmodifiableMap(sorted);
    }

    private static Validated validate(
            String code, String message, Map<String, String> context) {
        return new Validated(
                requireText(code, "code"),
                requireText(message, "message"),
                immutableContext(context));
    }

    private static String requireText(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + " must not be blank");
        }
        return value;
    }

    private record Validated(String code, String message, Map<String, String> context) {}
}
