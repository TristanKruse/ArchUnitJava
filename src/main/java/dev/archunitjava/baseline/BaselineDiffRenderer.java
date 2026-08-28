package dev.archunitjava.baseline;

import java.util.Objects;

/** Stable line-oriented baseline diff intended for code review. */
public final class BaselineDiffRenderer {
    private BaselineDiffRenderer() {}

    public static String render(BaselineComparison comparison) {
        BaselineComparison value = Objects.requireNonNull(comparison, "comparison");
        StringBuilder out = new StringBuilder("baseline-diff ")
                .append(ReviewedBaseline.CURRENT_SCHEMA_VERSION)
                .append(" on ").append(value.onDate()).append('\n');
        for (FindingChange change : value.changes()) {
            BaselineFinding finding = change.current().or(() -> change.previous()).orElseThrow();
            out.append(symbol(change.state())).append(' ').append(change.state()).append(' ')
                    .append(escape(finding.ruleId())).append(' ')
                    .append(escape(finding.violationId())).append(' ')
                    .append(finding.identityFingerprint());
            if (change.state() == FindingState.MOVED) {
                out.append(' ').append(change.previous().orElseThrow().evidenceFingerprint())
                        .append(" -> ").append(change.current().orElseThrow().evidenceFingerprint());
            }
            change.suppressionId().ifPresent(id -> out.append(" suppression=").append(escape(id)));
            out.append('\n');
        }
        return out.toString();
    }

    private static char symbol(FindingState state) {
        return switch (state) {
            case NEW -> '+';
            case UNCHANGED -> '=';
            case MOVED -> '~';
            case RESOLVED -> '-';
            case SUPPRESSED -> 's';
            case EXPIRED -> '!';
        };
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\t", "\\t").replace(" ", "\\s");
    }
}
