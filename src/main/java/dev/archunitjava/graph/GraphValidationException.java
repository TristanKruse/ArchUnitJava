package dev.archunitjava.graph;

/** Indicates that graph topology refers to a node absent from the graph. */
public final class GraphValidationException extends IllegalStateException {
    public GraphValidationException(String message) { super(message); }
}
