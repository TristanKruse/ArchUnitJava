package dev.archunitjava.execution;

import java.util.Map;

/** Invalid user input or API usage that can be corrected without changing the analyzer. */
public final class UserError extends ExecutionError {
    public UserError(String code, String message) {
        this(code, message, null, Map.of());
    }

    public UserError(
            String code, String message, Throwable cause, Map<String, String> context) {
        super(code, message, cause, context);
    }
}
