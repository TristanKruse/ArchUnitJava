package dev.archunitjava.execution;

import java.util.Map;

/** An unexpected infrastructure or analysis failure that prevented a reliable rule result. */
public final class TechnicalError extends ExecutionError {
    public TechnicalError(String code, String message) {
        this(code, message, null, Map.of());
    }

    public TechnicalError(
            String code, String message, Throwable cause, Map<String, String> context) {
        super(code, message, cause, context);
    }
}
