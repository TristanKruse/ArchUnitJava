package dev.archunitjava.rules;

/** Match policy for a boolean JPMS directive modifier. */
public enum DirectiveFlagPolicy {
    ANY,
    REQUIRED,
    FORBIDDEN;

    public boolean matches(boolean present) {
        return switch (this) {
            case ANY -> true;
            case REQUIRED -> present;
            case FORBIDDEN -> !present;
        };
    }
}
