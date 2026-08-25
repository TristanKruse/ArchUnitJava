package dev.archunitjava.importer;

import java.util.Objects;
import java.util.List;
import java.util.TreeSet;

/** Backend-neutral class header extracted from an untrusted class resource. */
public record ParsedClassFile(
        String binaryName,
        int accessFlags,
        int majorVersion,
        int minorVersion,
        boolean moduleDescriptor,
        String resourceName,
        ClassFileOrigin origin,
        int precedence,
        java.util.Optional<String> superclassBinaryName,
        List<String> interfaceBinaryNames,
        java.util.Optional<String> sourceFile,
        List<ParsedMember> declaredMembers,
        List<ParsedAnnotationOccurrence> annotations,
        List<ParsedAnnotationDefault> annotationDefaults,
        java.util.Optional<String> genericSignature,
        boolean recordDeclaration,
        List<ParsedRecordComponent> recordComponents,
        boolean sealedDeclaration,
        List<String> permittedSubclassBinaryNames)
        implements Comparable<ParsedClassFile> {
    public ParsedClassFile {
        if (binaryName == null || binaryName.isBlank()) {
            throw new IllegalArgumentException("binaryName must not be blank");
        }
        if (majorVersion < 0 || minorVersion < 0) {
            throw new IllegalArgumentException("class-file versions must not be negative");
        }
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }
        Objects.requireNonNull(origin, "origin");
        if (precedence < 0) throw new IllegalArgumentException("precedence must not be negative");
        Objects.requireNonNull(superclassBinaryName, "superclassBinaryName");
        superclassBinaryName = superclassBinaryName.map(value -> requireBinaryName(value, "superclass"));
        Objects.requireNonNull(interfaceBinaryNames, "interfaceBinaryNames");
        TreeSet<String> sortedInterfaces = new TreeSet<>();
        for (String interfaceName : interfaceBinaryNames) {
            sortedInterfaces.add(requireBinaryName(interfaceName, "interface"));
        }
        interfaceBinaryNames = List.copyOf(sortedInterfaces);
        Objects.requireNonNull(sourceFile, "sourceFile");
        sourceFile = sourceFile.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("sourceFile must not be blank");
            return value;
        });
        Objects.requireNonNull(declaredMembers, "declaredMembers");
        TreeSet<ParsedMember> sortedMembers = new TreeSet<>();
        for (ParsedMember member : declaredMembers) {
            sortedMembers.add(Objects.requireNonNull(member, "declaredMember"));
        }
        declaredMembers = List.copyOf(sortedMembers);
        annotations = sortedCopy(annotations, "annotation");
        annotationDefaults = sortedCopy(annotationDefaults, "annotationDefault");
        Objects.requireNonNull(genericSignature, "genericSignature");
        genericSignature = genericSignature.map(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("genericSignature must not be blank");
            return value;
        });
        recordComponents = sortedCopy(recordComponents, "recordComponent");
        if (!recordDeclaration && !recordComponents.isEmpty()) {
            throw new IllegalArgumentException("Only a record declaration can have record components");
        }
        Objects.requireNonNull(permittedSubclassBinaryNames, "permittedSubclassBinaryNames");
        permittedSubclassBinaryNames = permittedSubclassBinaryNames.stream()
                .map(value -> requireBinaryName(value, "permitted subclass"))
                .sorted()
                .toList();
        if (!sealedDeclaration && !permittedSubclassBinaryNames.isEmpty()) {
            throw new IllegalArgumentException("Permitted subclasses require a sealed declaration");
        }
    }

    public ParsedClassFile(
            String binaryName,
            int accessFlags,
            int majorVersion,
            int minorVersion,
            boolean moduleDescriptor,
            String resourceName,
            ClassFileOrigin origin,
            int precedence) {
        this(
                binaryName,
                accessFlags,
                majorVersion,
                minorVersion,
                moduleDescriptor,
                resourceName,
                origin,
                precedence,
                java.util.Optional.empty(),
                List.of(),
                java.util.Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                java.util.Optional.empty(),
                false,
                List.of(),
                false,
                List.of());
    }

    public ParsedClassFile(
            String binaryName,
            int accessFlags,
            int majorVersion,
            int minorVersion,
            boolean moduleDescriptor,
            String resourceName,
            ClassFileOrigin origin,
            int precedence,
            java.util.Optional<String> sourceFile,
            List<ParsedMember> declaredMembers) {
        this(
                binaryName,
                accessFlags,
                majorVersion,
                minorVersion,
                moduleDescriptor,
                resourceName,
                origin,
                precedence,
                java.util.Optional.empty(),
                List.of(),
                sourceFile,
                declaredMembers,
                List.of(),
                List.of(),
                java.util.Optional.empty(),
                false,
                List.of(),
                false,
                List.of());
    }

    public ParsedClassFile(
            String binaryName,
            int accessFlags,
            int majorVersion,
            int minorVersion,
            boolean moduleDescriptor,
            String resourceName,
            ClassFileOrigin origin,
            int precedence,
            java.util.Optional<String> superclassBinaryName,
            List<String> interfaceBinaryNames,
            java.util.Optional<String> sourceFile,
            List<ParsedMember> declaredMembers) {
        this(
                binaryName,
                accessFlags,
                majorVersion,
                minorVersion,
                moduleDescriptor,
                resourceName,
                origin,
                precedence,
                superclassBinaryName,
                interfaceBinaryNames,
                sourceFile,
                declaredMembers,
                List.of(),
                List.of(),
                java.util.Optional.empty(),
                false,
                List.of(),
                false,
                List.of());
    }

    public ParsedClassFile(
            String binaryName,
            int accessFlags,
            int majorVersion,
            int minorVersion,
            boolean moduleDescriptor,
            String resourceName,
            ClassFileOrigin origin,
            int precedence,
            java.util.Optional<String> superclassBinaryName,
            List<String> interfaceBinaryNames,
            java.util.Optional<String> sourceFile,
            List<ParsedMember> declaredMembers,
            List<ParsedAnnotationOccurrence> annotations,
            List<ParsedAnnotationDefault> annotationDefaults) {
        this(
                binaryName,
                accessFlags,
                majorVersion,
                minorVersion,
                moduleDescriptor,
                resourceName,
                origin,
                precedence,
                superclassBinaryName,
                interfaceBinaryNames,
                sourceFile,
                declaredMembers,
                annotations,
                annotationDefaults,
                java.util.Optional.empty(),
                false,
                List.of(),
                false,
                List.of());
    }

    public ParsedClassFile(
            String binaryName,
            int accessFlags,
            int majorVersion,
            int minorVersion,
            boolean moduleDescriptor,
            String resourceName,
            ClassFileOrigin origin,
            int precedence,
            java.util.Optional<String> superclassBinaryName,
            List<String> interfaceBinaryNames,
            java.util.Optional<String> sourceFile,
            List<ParsedMember> declaredMembers,
            List<ParsedAnnotationOccurrence> annotations,
            List<ParsedAnnotationDefault> annotationDefaults,
            java.util.Optional<String> genericSignature) {
        this(
                binaryName,
                accessFlags,
                majorVersion,
                minorVersion,
                moduleDescriptor,
                resourceName,
                origin,
                precedence,
                superclassBinaryName,
                interfaceBinaryNames,
                sourceFile,
                declaredMembers,
                annotations,
                annotationDefaults,
                genericSignature,
                false,
                List.of(),
                false,
                List.of());
    }

    private static <T extends Comparable<? super T>> List<T> sortedCopy(
            List<T> values, String name) {
        Objects.requireNonNull(values, name + "s");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, name))
                .sorted()
                .toList();
    }

    private static String requireBinaryName(String value, String role) {
        if (value == null || value.isBlank() || value.indexOf('/') >= 0) {
            throw new IllegalArgumentException(role + " binary name must not be blank or internal");
        }
        return value;
    }

    @Override
    public int compareTo(ParsedClassFile other) {
        int result = Integer.compare(precedence, other.precedence);
        if (result != 0) return result;
        result = binaryName.compareTo(other.binaryName);
        if (result != 0) return result;
        result = resourceName.compareTo(other.resourceName);
        return result != 0 ? result : origin.compareTo(other.origin);
    }
}
