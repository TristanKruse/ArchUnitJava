package dev.archunitjava.result;

import dev.archunitjava.graph.StableId;
import java.util.Objects;

/** A typed architecture subject and its machine-readable role in a violation. */
public record ViolationSubject(String role, StableId id) implements Comparable<ViolationSubject> {
    public ViolationSubject {
        role = ResultValues.requireText(role, "subject role");
        Objects.requireNonNull(id, "id");
    }

    @Override
    public int compareTo(ViolationSubject other) {
        int result = role.compareTo(other.role);
        return result != 0 ? result : id.compareTo(other.id);
    }
}
