package dev.archunitjava.importer;

/** Backend-neutral declared field or method extracted from a class file. */
public record ParsedMember(
        Kind kind,
        String name,
        String descriptor,
        int accessFlags,
        boolean hasCode,
        java.util.List<ParsedLineNumber> lineNumbers,
        java.util.Optional<String> genericSignature,
        java.util.List<ParsedCodeAccess> codeAccesses,
        java.util.List<ParsedDynamicCallSite> dynamicCallSites,
        java.util.List<ParsedExceptionEvidence> exceptionEvidence,
        java.util.List<ParsedMethodParameter> parameters)
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
        if (lineNumbers == null) throw new NullPointerException("lineNumbers");
        java.util.TreeSet<ParsedLineNumber> sortedLines = new java.util.TreeSet<>();
        for (ParsedLineNumber line : lineNumbers) {
            sortedLines.add(java.util.Objects.requireNonNull(line, "lineNumber"));
        }
        lineNumbers = java.util.List.copyOf(sortedLines);
        if (!hasCode && !lineNumbers.isEmpty()) {
            throw new IllegalArgumentException("members without bytecode cannot have line numbers");
        }
        java.util.Objects.requireNonNull(genericSignature, "genericSignature");
        genericSignature = genericSignature.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("genericSignature must not be blank");
            return value;
        });
        if (codeAccesses == null) throw new NullPointerException("codeAccesses");
        codeAccesses = codeAccesses.stream()
                .map(value -> java.util.Objects.requireNonNull(value, "codeAccess"))
                .sorted()
                .toList();
        if (!hasCode && !codeAccesses.isEmpty()) {
            throw new IllegalArgumentException("members without bytecode cannot have code accesses");
        }
        if (dynamicCallSites == null) throw new NullPointerException("dynamicCallSites");
        dynamicCallSites = dynamicCallSites.stream()
                .map(value -> java.util.Objects.requireNonNull(value, "dynamicCallSite"))
                .sorted()
                .toList();
        if (!hasCode && !dynamicCallSites.isEmpty()) {
            throw new IllegalArgumentException("members without bytecode cannot have dynamic call sites");
        }
        if (exceptionEvidence == null) throw new NullPointerException("exceptionEvidence");
        exceptionEvidence = exceptionEvidence.stream()
                .map(value -> java.util.Objects.requireNonNull(value, "exceptionEvidence"))
                .sorted()
                .toList();
        if (kind == Kind.FIELD && !exceptionEvidence.isEmpty()) {
            throw new IllegalArgumentException("fields cannot have exception evidence");
        }
        if (!hasCode && exceptionEvidence.stream()
                .anyMatch(value -> value.kind() != ParsedExceptionEvidence.Kind.DECLARED_THROWS)) {
            throw new IllegalArgumentException("members without bytecode cannot have handler or throw evidence");
        }
        if (parameters == null) throw new NullPointerException("parameters");
        parameters = parameters.stream()
                .map(value -> java.util.Objects.requireNonNull(value, "parameter"))
                .sorted()
                .toList();
        if (kind == Kind.FIELD && !parameters.isEmpty()) {
            throw new IllegalArgumentException("fields cannot have method parameters");
        }
        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index).index() != index) {
                throw new IllegalArgumentException("parameter indexes must be contiguous from zero");
            }
        }
    }

    public ParsedMember(
            Kind kind,
            String name,
            String descriptor,
            int accessFlags,
            boolean hasCode,
            java.util.List<ParsedLineNumber> lineNumbers,
            java.util.Optional<String> genericSignature,
            java.util.List<ParsedCodeAccess> codeAccesses,
            java.util.List<ParsedDynamicCallSite> dynamicCallSites,
            java.util.List<ParsedExceptionEvidence> exceptionEvidence) {
        this(
                kind,
                name,
                descriptor,
                accessFlags,
                hasCode,
                lineNumbers,
                genericSignature,
                codeAccesses,
                dynamicCallSites,
                exceptionEvidence,
                java.util.List.of());
    }

    public ParsedMember(
            Kind kind, String name, String descriptor, int accessFlags, boolean hasCode) {
        this(
                kind,
                name,
                descriptor,
                accessFlags,
                hasCode,
                java.util.List.of(),
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());
    }

    public ParsedMember(
            Kind kind,
            String name,
            String descriptor,
            int accessFlags,
            boolean hasCode,
            java.util.List<ParsedLineNumber> lineNumbers) {
        this(
                kind,
                name,
                descriptor,
                accessFlags,
                hasCode,
                lineNumbers,
                java.util.Optional.empty(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());
    }

    public ParsedMember(
            Kind kind,
            String name,
            String descriptor,
            int accessFlags,
            boolean hasCode,
            java.util.List<ParsedLineNumber> lineNumbers,
            java.util.Optional<String> genericSignature) {
        this(
                kind,
                name,
                descriptor,
                accessFlags,
                hasCode,
                lineNumbers,
                genericSignature,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());
    }

    public ParsedMember(
            Kind kind,
            String name,
            String descriptor,
            int accessFlags,
            boolean hasCode,
            java.util.List<ParsedLineNumber> lineNumbers,
            java.util.Optional<String> genericSignature,
            java.util.List<ParsedCodeAccess> codeAccesses) {
        this(
                kind,
                name,
                descriptor,
                accessFlags,
                hasCode,
                lineNumbers,
                genericSignature,
                codeAccesses,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());

    }

    public ParsedMember(
            Kind kind,
            String name,
            String descriptor,
            int accessFlags,
            boolean hasCode,
            java.util.List<ParsedLineNumber> lineNumbers,
            java.util.Optional<String> genericSignature,
            java.util.List<ParsedCodeAccess> codeAccesses,
            java.util.List<ParsedDynamicCallSite> dynamicCallSites) {
        this(
                kind,
                name,
                descriptor,
                accessFlags,
                hasCode,
                lineNumbers,
                genericSignature,
                codeAccesses,
                dynamicCallSites,
                java.util.List.of(),
                java.util.List.of());
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
