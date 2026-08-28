package dev.archunitjava.report;

import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;

/** Generated syntax-safe aliases shared by deterministic renderers. */
final class ReportAliases {
    private ReportAliases() {}

    static Map<String, String> nodes(GraphSnapshot snapshot) {
        TreeMap<String, String> result = new TreeMap<>();
        for (int index = 0; index < snapshot.nodes().size(); index++) {
            result.put(
                    snapshot.nodes().get(index).id(),
                    "n" + String.format(Locale.ROOT, "%06d", index + 1));
        }
        return Map.copyOf(result);
    }
}
