package dev.archunitjava.diagram.plantuml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlantUmlParserTest {
    @Test
    void parsesComponentsAliasesArrowsAndApprovedStereotypesDeterministically() {
        String source = """
                @startuml
                component "Service" as service <<layer>>
                component "Core \\"API\\"\\\\boundary" as core <<layer>>
                service ..> core
                @enduml
                """;

        PlantUmlDiagram diagram = PlantUmlParser.parse(source, Set.of("layer"));

        assertEquals(List.of("core", "service"), diagram.components().stream()
                .map(PlantUmlComponent::alias).toList());
        assertEquals("Core \"API\"\\boundary", diagram.component("core").displayName());
        assertEquals(PlantUmlArrow.DASHED, diagram.edges().getFirst().arrow());
    }

    @Test
    void rejectsIncludesMacrosUnknownSyntaxAndUnapprovedStereotypes() {
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "!include https://example.invalid/diagram.puml", Set.of()));
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "!define NAME value", Set.of()));
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "skinparam componentStyle rectangle", Set.of()));
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "component \"A\" as a <<unapproved>>", Set.of("layer")));
    }

    @Test
    void enforcesBoundsBeforeBuildingAnUnboundedModel() {
        PlantUmlLimits limits = new PlantUmlLimits(80, 4, 40, 1, 1);
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "component \"A\" as a\ncomponent \"B\" as b", Set.of(), limits));
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "component \"This component line is deliberately too long\" as a",
                Set.of(), limits));
    }

    @Test
    void rejectsAmbiguousDuplicateAndUnknownEdges() {
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "component \"A\" as a\ncomponent \"Other A\" as a", Set.of()));
        assertThrows(InvalidPlantUmlException.class, () -> PlantUmlParser.parse(
                "component \"A\" as a\na --> missing", Set.of()));
        InvalidPlantUmlException self = assertThrows(
                InvalidPlantUmlException.class,
                () -> PlantUmlParser.parse("component \"A\" as a\na --> a", Set.of()));
        assertTrue(self.getMessage().contains("self"));
    }
}
