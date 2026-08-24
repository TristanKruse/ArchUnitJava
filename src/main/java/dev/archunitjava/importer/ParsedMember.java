package dev.archunitjava.importer;

/** Backend-neutral declared field or method extracted from a class file. */
public record ParsedMember(
        Kind kind, String name, String descriptor, int accessFlags, boolean hasCode)
        implements Comparable<ParsedMember> {
    public enum Kind {
        FIELD,
        METHOD
    }

    public ParsedMember {
        if (kind == null) throw new NullPointerException("kind");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("descriptor must not be blank");
        }
        if (kind == Kind.FIELD && hasCode) {
            throw new IllegalArgumentException("fields cannot contain bytecode");
        }
    }

    @Override
    public int compareTo(ParsedMember other) {
        int result = kind.compareTo(other.kind);
        if (result != 0) return result;
        result = name.compareTo(other.name);
        if (result != 0) return result;
        result = descriptor.compareTo(other.descriptor);
        return result != 0 ? result : Integer.compareUnsigned(accessFlags, other.accessFlags);
    }
}
