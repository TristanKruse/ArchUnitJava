package dev.archunitjava.diagram.plantuml;

/** Rejection of input outside the bounded, non-executable PlantUML subset. */
public final class InvalidPlantUmlException extends IllegalArgumentException {
    public InvalidPlantUmlException(String message) {
        super(message);
    }
}
