package dev.archunitjava.importer;

/** Internal backend seam that exposes only library-owned model values. */
interface ClassFileParserBackend {
    ParsedClassHeader parse(byte[] bytes, TraversalObserver observer);

    record ParsedClassHeader(
            String binaryName,
            int accessFlags,
            int majorVersion,
            int minorVersion,
            boolean moduleDescriptor) {}

    @FunctionalInterface
    interface TraversalObserver {
        void phase(ClassFileTraversalPhase phase);
    }
}
