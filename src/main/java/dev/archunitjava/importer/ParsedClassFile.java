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
        List<ParsedMember> declaredMembers)
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
                declaredMembers);
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
