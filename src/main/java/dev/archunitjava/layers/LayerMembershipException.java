package dev.archunitjava.layers;

/** Missing required layer or ambiguous membership under the configured policy. */
public final class LayerMembershipException extends IllegalArgumentException {
    public LayerMembershipException(String message) {
        super(message);
    }
}
