package dev.archunitjava.diagram.plantuml;

/** Hard parser bounds applied before and during parsing. */
public record PlantUmlLimits(
        int maxCharacters,
        int maxLines,
        int maxLineLength,
        int maxComponents,
        int maxEdges) {
    public PlantUmlLimits {
        positive(maxCharacters, "maxCharacters");
        positive(maxLines, "maxLines");
        positive(maxLineLength, "maxLineLength");
        positive(maxComponents, "maxComponents");
        positive(maxEdges, "maxEdges");
    }

    public static PlantUmlLimits defaults() {
        return new PlantUmlLimits(1_000_000, 10_000, 4_096, 2_000, 20_000);
    }

    private static void positive(int value, String role) {
        if (value <= 0) throw new IllegalArgumentException(role + " must be positive");
    }
}
