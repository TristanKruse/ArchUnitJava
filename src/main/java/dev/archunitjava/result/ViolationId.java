package dev.archunitjava.result;

/** Stable machine identity for a violation, independent of rendered prose. */
public record ViolationId(String value) implements Comparable<ViolationId> {
    public ViolationId {
        value = ResultValues.requireText(value, "violation id");
    }

    @Override
    public int compareTo(ViolationId other) {
        return value.compareTo(other.value);
    }
}
