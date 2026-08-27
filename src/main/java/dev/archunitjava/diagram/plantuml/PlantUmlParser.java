package dev.archunitjava.diagram.plantuml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Non-executing parser for a deliberately small PlantUML component grammar. */
public final class PlantUmlParser {
    private static final Pattern COMPONENT = Pattern.compile(
            "component\\s+\"((?:[^\"\\\\\\p{Cntrl}]|\\\\[\\\\\"nrt]|\\\\u[0-9A-Fa-f]{4})+)\""
                    + "\\s+as\\s+([A-Za-z][A-Za-z0-9_-]*)"
                    + "(?:\\s+<<([A-Za-z][A-Za-z0-9_-]*)>>)?");
    private static final Pattern EDGE = Pattern.compile(
            "([A-Za-z][A-Za-z0-9_-]*)\\s+(-->|\\.\\.>)\\s+([A-Za-z][A-Za-z0-9_-]*)");

    private PlantUmlParser() {}

    public static PlantUmlDiagram parse(String source, Set<String> approvedStereotypes) {
        return parse(source, approvedStereotypes, PlantUmlLimits.defaults());
    }

    public static PlantUmlDiagram parse(
            String source,
            Set<String> approvedStereotypes,
            PlantUmlLimits limits) {
        String text = Objects.requireNonNull(source, "source");
        PlantUmlLimits bounds = Objects.requireNonNull(limits, "limits");
        Set<String> stereotypes = approved(approvedStereotypes);
        if (text.length() > bounds.maxCharacters()) {
            throw invalid(0, "input exceeds character limit");
        }
        String[] lines = text.split("\\R", -1);
        if (lines.length > bounds.maxLines()) throw invalid(0, "input exceeds line limit");
        List<PlantUmlComponent> components = new ArrayList<>();
        List<PlantUmlEdge> edges = new ArrayList<>();
        boolean started = false;
        boolean ended = false;
        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            int lineNumber = index + 1;
            if (raw.length() > bounds.maxLineLength()) {
                throw invalid(lineNumber, "line exceeds length limit");
            }
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("'")) continue;
            if (line.startsWith("!")) {
                throw invalid(lineNumber, "directives and macros are not supported");
            }
            if (line.equals("@startuml")) {
                if (started || ended || !components.isEmpty() || !edges.isEmpty()) {
                    throw invalid(lineNumber, "misplaced @startuml");
                }
                started = true;
                continue;
            }
            if (line.equals("@enduml")) {
                if (!started || ended) throw invalid(lineNumber, "misplaced @enduml");
                ended = true;
                continue;
            }
            if (ended) throw invalid(lineNumber, "content after @enduml");
            Matcher component = COMPONENT.matcher(line);
            if (component.matches()) {
                Optional<String> stereotype = Optional.ofNullable(component.group(3));
                stereotype.ifPresent(value -> {
                    if (!stereotypes.contains(value)) {
                        throw invalid(lineNumber, "unapproved stereotype: " + value);
                    }
                });
                try {
                    components.add(new PlantUmlComponent(
                            unescape(component.group(1), lineNumber),
                            component.group(2),
                            stereotype));
                } catch (IllegalArgumentException error) {
                    if (error instanceof InvalidPlantUmlException invalid) throw invalid;
                    throw invalid(lineNumber, error.getMessage());
                }
                if (components.size() > bounds.maxComponents()) {
                    throw invalid(lineNumber, "component limit exceeded");
                }
                continue;
            }
            Matcher edge = EDGE.matcher(line);
            if (edge.matches()) {
                if (edge.group(1).equals(edge.group(3))) {
                    throw invalid(lineNumber, "component self edges are not supported");
                }
                edges.add(new PlantUmlEdge(
                        edge.group(1), edge.group(3), PlantUmlArrow.fromToken(edge.group(2))));
                if (edges.size() > bounds.maxEdges()) {
                    throw invalid(lineNumber, "edge limit exceeded");
                }
                continue;
            }
            throw invalid(lineNumber, "unsupported PlantUML syntax");
        }
        if (started != ended) throw invalid(0, "@startuml and @enduml must be paired");
        return new PlantUmlDiagram(components, edges);
    }

    private static String unescape(String value, int line) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                result.append(current);
                continue;
            }
            if (++index >= value.length()) throw invalid(line, "trailing escape");
            char escaped = value.charAt(index);
            switch (escaped) {
                case '\\' -> result.append('\\');
                case '"' -> result.append('"');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) throw invalid(line, "incomplete Unicode escape");
                    String digits = value.substring(index + 1, index + 5);
                    try {
                        result.append((char) Integer.parseInt(digits, 16));
                    } catch (NumberFormatException error) {
                        throw invalid(line, "invalid Unicode escape");
                    }
                    index += 4;
                }
                default -> throw invalid(line, "unsupported escape: \\" + escaped);
            }
        }
        return result.toString();
    }

    private static Set<String> approved(Set<String> values) {
        Objects.requireNonNull(values, "approvedStereotypes");
        HashSet<String> result = new HashSet<>();
        values.forEach(value -> result.add(PlantUmlComponent.alias(value)));
        return Set.copyOf(result);
    }

    private static InvalidPlantUmlException invalid(int line, String detail) {
        return new InvalidPlantUmlException(
                line > 0 ? "PlantUML line " + line + ": " + detail : "PlantUML: " + detail);
    }
}
