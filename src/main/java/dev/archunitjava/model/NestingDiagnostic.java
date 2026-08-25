package dev.archunitjava.model;

import java.util.Objects;

public record NestingDiagnostic(NestingDiagnosticCode code, String evidence)
        implements Comparable<NestingDiagnostic> {
    public NestingDiagnostic {
        Objects.requireNonNull(code, "code");
        if (evidence == null) throw new NullPointerException("evidence");
    }

    @Override
    public int compareTo(NestingDiagnostic other) {
        int result = code.compareTo(other.code);
        return result != 0 ? result : evidence.compareTo(other.evidence);
    }
}
