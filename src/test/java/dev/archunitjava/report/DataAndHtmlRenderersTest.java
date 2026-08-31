package dev.archunitjava.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.DependencyGraph;
import dev.archunitjava.graph.DependencyKind;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.layers.LayerId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataAndHtmlRenderersTest {
    private static final TypeId A = TypeId.ofBinaryName("app.A");
    private static final TypeId B = TypeId.ofBinaryName("app.B");
    private static final String HOSTILE =
            "</script><img src=x onerror=alert(1)> \" link: https://evil.invalid %%{init}";

    @Test
    void jsonPreservesTheVersionedSchemaCountsEvidenceAndStableIds() {
        GraphSnapshot snapshot = snapshot(false);

        String json = JsonGraphRenderer.render(snapshot);

        assertTrue(json.startsWith("{\"schemaVersion\":\"archunitjava.graph-snapshot.v1\""));
        assertTrue(json.contains("\"evidenceCount\":2"));
        assertTrue(json.contains("\"sourceEdgeCount\":1"));
        assertTrue(json.contains("\"includedKinds\":["));
        assertTrue(json.contains("layer:\\u003c/script\\u003e"));
        assertFalse(json.contains("</script>"));
        assertTrue(json.endsWith("\n"));
    }

    @Test
    void csvUsesTypedRowsAndRoundTripsQuotedStableValues() {
        GraphSnapshot snapshot = snapshot(false);

        List<List<String>> rows = parseCsv(CsvGraphRenderer.render(snapshot));

        assertTrue(rows.stream().allMatch(row -> row.size() == 18));
        assertEquals("schema_version", rows.getFirst().getFirst());
        List<String> hostileNode = rows.stream()
                .filter(row -> row.get(1).equals("node") && row.get(5).equals(HOSTILE))
                .findFirst().orElseThrow();
        assertEquals("layer:" + HOSTILE, hostileNode.get(2));
        List<String> edge = rows.stream()
                .filter(row -> row.get(1).equals("edge"))
                .findFirst().orElseThrow();
        assertEquals("1", edge.get(8));
        assertEquals("2", edge.get(9));
        assertTrue(rows.stream().anyMatch(row ->
                row.get(1).equals("metadata") && row.get(16).equals("domain")
                        && row.get(17).equals("LAYER")));
        assertTrue(rows.stream().anyMatch(row -> row.get(1).equals("evidence")));
    }

    @Test
    void csvIsSpreadsheetSafeByDefaultAndOffersExplicitLosslessInterchange() {
        GraphSnapshot snapshot = snapshotWithFormulaLabel();

        List<List<String>> safe = parseCsv(CsvGraphRenderer.render(snapshot));
        List<List<String>> machine = parseCsv(CsvGraphRenderer.renderMachineReadable(snapshot));

        assertEquals("'=2+2", safe.stream()
                .filter(row -> row.get(1).equals("node"))
                .findFirst().orElseThrow().get(5));
        assertEquals("=2+2", machine.stream()
                .filter(row -> row.get(1).equals("node"))
                .findFirst().orElseThrow().get(5));
    }

    @Test
    void d2UsesGeneratedIdentifiersAndCannotReceivePropertiesFromNames() {
        String d2 = D2GraphRenderer.render(snapshot(false));

        assertTrue(d2.contains("n000001:"));
        assertTrue(d2.contains("\\u003c/script\\u003e"));
        assertFalse(d2.contains("</script>"));
        assertFalse(d2.lines().anyMatch(line -> line.stripLeading().startsWith("link:")));
        assertFalse(d2.contains("\r"));
    }

    @Test
    void htmlIsSelfContainedInteractiveAndEscapesAllGraphStrings() {
        GraphSnapshot snapshot = snapshot(false);

        String html = HtmlGraphRenderer.render(snapshot);

        assertTrue(html.contains("Content-Security-Policy"));
        assertTrue(html.contains("default-src 'none'"));
        assertTrue(html.contains("document.querySelectorAll('[data-search]')"));
        assertTrue(html.contains("&lt;/script&gt;&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains("<script src="));
        assertFalse(html.contains("<link rel="));
        assertFalse(html.contains("<img src=x"));
        assertEquals(1, occurrences(html, "</script>"));
    }

    @Test
    void htmlRefusesEveryOversizedDimensionBeforeRendering() {
        GraphSnapshot snapshot = snapshot(false);

        assertThrows(HtmlRenderLimitException.class, () -> HtmlGraphRenderer.render(
                snapshot, new HtmlRenderLimits(1, 10, 10, 100_000)));
        assertThrows(HtmlRenderLimitException.class, () -> HtmlGraphRenderer.render(
                snapshot, new HtmlRenderLimits(10, 1, 10, 100_000)));
        assertThrows(HtmlRenderLimitException.class, () -> HtmlGraphRenderer.render(
                snapshot, new HtmlRenderLimits(10, 10, 1, 100_000)));
        assertThrows(HtmlRenderLimitException.class, () -> HtmlGraphRenderer.render(
                snapshot, new HtmlRenderLimits(10, 10, 10, 10)));
    }

    @Test
    void everyRendererIsByteStableForEquivalentSnapshots() {
        GraphSnapshot first = snapshot(false);
        GraphSnapshot second = snapshot(true);

        assertEquals(first, second);
        assertArrayEquals(JsonGraphRenderer.renderBytes(first), JsonGraphRenderer.renderBytes(second));
        assertArrayEquals(CsvGraphRenderer.renderBytes(first), CsvGraphRenderer.renderBytes(second));
        assertArrayEquals(D2GraphRenderer.renderBytes(first), D2GraphRenderer.renderBytes(second));
        assertArrayEquals(HtmlGraphRenderer.renderBytes(first), HtmlGraphRenderer.renderBytes(second));
    }

    @Test
    void outputDoesNotDependOnTheProcessFormattingLocale() {
        GraphSnapshot snapshot = snapshot(false);
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ROOT);
            List<byte[]> root = renderedBytes(snapshot);
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            List<byte[]> arabic = renderedBytes(snapshot);
            for (int index = 0; index < root.size(); index++) {
                assertArrayEquals(root.get(index), arabic.get(index));
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    private static GraphSnapshot snapshot(boolean reverse) {
        LinkedHashMap<TypeId, LayerId> mappings = new LinkedHashMap<>();
        if (reverse) {
            mappings.put(B, LayerId.named("safe"));
            mappings.put(A, LayerId.named(HOSTILE));
        } else {
            mappings.put(A, LayerId.named(HOSTILE));
            mappings.put(B, LayerId.named("safe"));
        }
        DependencyEvidence first = DependencyEvidence.at(
                LocationId.ofResourcePath("classes/<first>.class"));
        DependencyEvidence second = new DependencyEvidence(
                LocationId.ofResourcePath("classes/second.class"),
                java.util.Optional.empty(),
                java.util.OptionalInt.empty(),
                java.util.Optional.of("evil\"<tag>.java"),
                java.util.OptionalInt.of(12));
        DependencyGraph.Builder graph = DependencyGraph.builder().addNode(A).addNode(B);
        List<DependencyEvidence> evidence = reverse
                ? List.of(second, first) : List.of(first, second);
        evidence.forEach(item -> graph.addDependency(
                A, B, DependencyKind.METHOD_CALL, item));
        graph.addDependency(B, A, DependencyKind.FIELD_TYPE, first);
        return GraphSnapshotQuery.layers(graph.build(), mappings).snapshot();
    }

    private static GraphSnapshot snapshotWithFormulaLabel() {
        TypeId formula = TypeId.ofBinaryName("app.Formula");
        DependencyGraph graph = DependencyGraph.builder().addNode(formula).build();
        return GraphSnapshotQuery.layers(graph, Map.of(formula, LayerId.named("=2+2"))).snapshot();
    }

    private static List<byte[]> renderedBytes(GraphSnapshot snapshot) {
        return List.of(
                DotGraphRenderer.renderBytes(snapshot),
                MermaidGraphRenderer.renderBytes(snapshot),
                JsonGraphRenderer.renderBytes(snapshot),
                CsvGraphRenderer.renderBytes(snapshot),
                D2GraphRenderer.renderBytes(snapshot),
                HtmlGraphRenderer.renderBytes(snapshot));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char character = csv.charAt(index);
            if (quoted) {
                if (character == '"' && index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else if (character == '"') {
                    quoted = false;
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                row.add(field.toString());
                rows.add(List.copyOf(row));
                row.clear();
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        assertFalse(quoted);
        assertTrue(row.isEmpty());
        return rows;
    }
}
