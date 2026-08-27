package dev.archunitjava.report;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded self-contained HTML report with constant inline CSS/JS and escaped graph data. */
public final class HtmlGraphRenderer {
    private HtmlGraphRenderer() {}

    public static String render(GraphSnapshot snapshot) {
        return render(snapshot, HtmlRenderLimits.defaults());
    }

    public static String render(GraphSnapshot snapshot, HtmlRenderLimits limits) {
        GraphSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        HtmlRenderLimits bounds = Objects.requireNonNull(limits, "limits");
        enforce(value, bounds);
        StringBuilder out = new StringBuilder();
        out.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta http-equiv=\"Content-Security-Policy\" content=\"")
                .append("default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; ")
                .append("img-src 'none'; connect-src 'none'; base-uri 'none'; form-action 'none'\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
                .append("<title>ArchUnitJava graph snapshot</title>\n")
                .append("<style>body{font:14px system-ui,sans-serif;margin:2rem;color:#17202a}")
                .append("table{border-collapse:collapse;width:100%;margin:1rem 0}")
                .append("th,td{border:1px solid #ccd1d1;padding:.45rem;text-align:left;vertical-align:top}")
                .append("code{overflow-wrap:anywhere}input{padding:.5rem;width:min(40rem,100%)}")
                .append(".meta{display:flex;gap:1rem;flex-wrap:wrap}.warning{color:#922b21}")
                .append("[hidden]{display:none}</style>\n</head>\n<body>\n")
                .append("<h1>Architecture graph</h1>\n<div class=\"meta\">");
        metric(out, "Schema", value.schemaVersion());
        metric(out, "Domain", value.query().domain().name());
        metric(out, "Nodes", Integer.toString(value.nodes().size()));
        metric(out, "Edges", Integer.toString(value.edges().size()));
        metric(out, "Truncated", Boolean.toString(value.query().truncated()));
        metric(out, "Omitted nodes", Integer.toString(value.query().omittedNodeCount()));
        metric(out, "Omitted edges", Integer.toString(value.query().omittedEdgeCount()));
        metric(out, "Omitted evidence", Integer.toString(value.query().omittedEvidenceCount()));
        out.append("</div>\n<label for=\"filter\">Filter nodes and edges</label> ")
                .append("<input id=\"filter\" type=\"search\" autocomplete=\"off\">\n")
                .append("<h2>Nodes</h2>\n<table><thead><tr><th>Label</th><th>Stable ID</th>")
                .append("<th>Collapsed sources</th></tr></thead><tbody>\n");
        for (SnapshotNode node : value.nodes()) {
            String search = node.label() + " " + node.id();
            out.append("<tr data-search=\"").append(ReportEscapes.html(search)).append("\"><td>")
                    .append(ReportEscapes.html(node.label())).append("</td><td><code>")
                    .append(ReportEscapes.html(node.id())).append("</code></td><td>")
                    .append(node.sourceNodeCount()).append("</td></tr>\n");
        }
        out.append("</tbody></table>\n<h2>Edges</h2>\n<table><thead><tr><th>Origin</th>")
                .append("<th>Target</th><th>Kinds</th><th>Dependencies</th><th>Evidence</th>")
                .append("</tr></thead><tbody>\n");
        for (SnapshotEdge edge : value.edges()) {
            String search = edge.originId() + " " + edge.targetId() + " "
                    + String.join(" ", edge.dependencyKinds());
            out.append("<tr data-search=\"").append(ReportEscapes.html(search)).append("\"><td><code>")
                    .append(ReportEscapes.html(edge.originId())).append("</code></td><td><code>")
                    .append(ReportEscapes.html(edge.targetId())).append("</code></td><td>")
                    .append(ReportEscapes.html(String.join(", ", edge.dependencyKinds())))
                    .append("</td><td>").append(edge.sourceEdgeCount()).append("</td><td>")
                    .append("<details><summary>").append(edge.evidenceCount()).append(" total, ")
                    .append(edge.omittedEvidenceCount()).append(" omitted</summary><ul>");
            for (SnapshotEvidence evidence : edge.evidence()) {
                out.append("<li><code>").append(ReportEscapes.html(evidence.locationId()))
                        .append("</code>");
                evidence.ownerMemberId().ifPresent(member -> out.append(" — ")
                        .append(ReportEscapes.html(member)));
                evidence.sourceFile().ifPresent(file -> out.append(" — ")
                        .append(ReportEscapes.html(file)));
                if (evidence.lineNumber().isPresent()) {
                    out.append(':').append(evidence.lineNumber().getAsInt());
                }
                out.append("</li>");
            }
            out.append("</ul></details></td></tr>\n");
        }
        out.append("</tbody></table>\n<script>")
                .append("(()=>{'use strict';const f=document.getElementById('filter');")
                .append("f.addEventListener('input',()=>{const q=f.value.toLocaleLowerCase();")
                .append("document.querySelectorAll('[data-search]').forEach(r=>{")
                .append("r.hidden=!r.dataset.search.toLocaleLowerCase().includes(q);});});})();")
                .append("</script>\n</body>\n</html>\n");
        return out.toString();
    }

    public static byte[] renderBytes(GraphSnapshot snapshot, HtmlRenderLimits limits) {
        return render(snapshot, limits).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] renderBytes(GraphSnapshot snapshot) {
        return render(snapshot).getBytes(StandardCharsets.UTF_8);
    }

    private static void enforce(GraphSnapshot snapshot, HtmlRenderLimits limits) {
        long evidence = snapshot.edges().stream().mapToLong(edge -> edge.evidence().size()).sum();
        long targetCharacters = snapshot.nodes().stream()
                        .mapToLong(node -> (long) node.id().length() + node.label().length())
                        .sum()
                + snapshot.edges().stream()
                        .mapToLong(edge -> (long) edge.id().length()
                                + edge.originId().length()
                                + edge.targetId().length()
                                + edge.dependencyKinds().stream().mapToInt(String::length).sum()
                                + edge.evidence().stream().mapToLong(HtmlGraphRenderer::characters).sum())
                        .sum();
        if (snapshot.nodes().size() > limits.maxNodes()) {
            throw new HtmlRenderLimitException("Snapshot exceeds HTML node limit");
        }
        if (snapshot.edges().size() > limits.maxEdges()) {
            throw new HtmlRenderLimitException("Snapshot exceeds HTML edge limit");
        }
        if (evidence > limits.maxEvidenceItems()) {
            throw new HtmlRenderLimitException("Snapshot exceeds HTML evidence limit");
        }
        if (targetCharacters > limits.maxTargetCharacters()) {
            throw new HtmlRenderLimitException("Snapshot exceeds HTML target-text limit");
        }
    }

    private static long characters(SnapshotEvidence evidence) {
        return evidence.locationId().length()
                + evidence.ownerMemberId().map(String::length).orElse(0)
                + evidence.sourceFile().map(String::length).orElse(0);
    }

    private static void metric(StringBuilder out, String label, String value) {
        out.append("<span><strong>").append(label).append(":</strong> ")
                .append(ReportEscapes.html(value)).append("</span>");
    }
}
