package dev.archunitjava.importer;

/** Backend-neutral direct method-handle constant. */
public record ParsedMethodHandle(
        String kind,
        int referenceKind,
        String ownerDescriptor,
        String name,
        String lookupDescriptor,
        boolean ownerInterface)
        implements Comparable<ParsedMethodHandle> {
    public ParsedMethodHandle {
        kind = text(kind, "kind");
        if (referenceKind < 1 || referenceKind > 9) {
            throw new IllegalArgumentException("referenceKind must be between 1 and 9");
        }
        ownerDescriptor = text(ownerDescriptor, "ownerDescriptor");
        name = text(name, "name");
        lookupDescriptor = text(lookupDescriptor, "lookupDescriptor");
    }

    @Override
    public int compareTo(ParsedMethodHandle other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = ownerDescriptor.compareTo(other.ownerDescriptor);
        if (result != 0) return result;
        result = name.compareTo(other.name);
        return result != 0 ? result : lookupDescriptor.compareTo(other.lookupDescriptor);
    }

    private static String text(String value, String role) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(role + " must not be blank");
        return value;
    }
}
