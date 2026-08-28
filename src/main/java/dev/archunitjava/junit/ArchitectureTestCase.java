package dev.archunitjava.junit;

import java.util.Objects;

/** Framework-neutral executable case that can be adapted to a JUnit Jupiter DynamicTest. */
public record ArchitectureTestCase(
        String semanticIdentity, String displayName, Runnable executable)
        implements Comparable<ArchitectureTestCase> {
    public ArchitectureTestCase {
        semanticIdentity = requireText(semanticIdentity, "semanticIdentity");
        displayName = requireText(displayName, "displayName");
        Objects.requireNonNull(executable, "executable");
    }

    public void execute() {
        executable.run();
    }

    @Override
    public int compareTo(ArchitectureTestCase other) {
        int result = semanticIdentity.compareTo(other.semanticIdentity);
        return result != 0 ? result : displayName.compareTo(other.displayName);
    }

    private static String requireText(String value, String role) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value;
    }
}
