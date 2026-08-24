package dev.archunitjava.result;

import java.util.Map;
import java.util.Objects;

/** Structured analysis or execution information that renderers may later turn into prose. */
public record Diagnostic(String code, Severity severity, Map<String, String> context)
        implements Comparable<Diagnostic> {
    public Diagnostic {
        code = ResultValues.requireText(code, "diagnostic code");
        Objects.requireNonNull(severity, "severity");
        context = ResultValues.sortedTextMap(context, "diagnostic context");
    }

    @Override
    public int compareTo(Diagnostic other) {
        int result = code.compareTo(other.code);
        if (result != 0) return result;
        result = severity.compareTo(other.severity);
        return result != 0 ? result : ResultValues.compareMaps(context, other.context);
    }
}
