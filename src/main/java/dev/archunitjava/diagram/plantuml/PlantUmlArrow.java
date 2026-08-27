package dev.archunitjava.diagram.plantuml;

/** Safe directed-arrow forms supported by the bounded parser. */
public enum PlantUmlArrow {
    SOLID("-->"),
    DASHED("..>");

    private final String token;

    PlantUmlArrow(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    static PlantUmlArrow fromToken(String value) {
        return switch (value) {
            case "-->" -> SOLID;
            case "..>" -> DASHED;
            default -> throw new InvalidPlantUmlException("Unsupported arrow: " + value);
        };
    }
}
