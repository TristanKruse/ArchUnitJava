package dev.archunitjava.selector;

/** Stable, human-readable selector metadata used by diagnostics and later rule descriptions. */
public record SelectorDescription(String text) implements Comparable<SelectorDescription> {
    public SelectorDescription {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Selector description must not be blank");
        }
    }

    @Override
    public int compareTo(SelectorDescription other) {
        return text.compareTo(other.text);
    }

    @Override
    public String toString() {
        return text;
    }
}
