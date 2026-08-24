package dev.archunitjava.importer;

/** Internal backend seam that exposes only library-owned model values. */
interface ClassFileParserBackend {
    ParsedClassHeader parse(byte[] bytes, TraversalObserver observer);

    record ParsedClassHeader(
            String binaryName,
            int accessFlags,
            int majorVersion,
            int minorVersion,
            boolean moduleDescriptor,
            java.util.Optional<String> superclassBinaryName,
            java.util.List<String> interfaceBinaryNames,
            java.util.Optional<String> sourceFile,
            java.util.List<ParsedMember> declaredMembers,
            java.util.List<ParsedAnnotationOccurrence> annotations,
            java.util.List<ParsedAnnotationDefault> annotationDefaults,
            java.util.Optional<String> genericSignature) {}

    @FunctionalInterface
    interface TraversalObserver {
        void phase(ClassFileTraversalPhase phase);
    }
}
