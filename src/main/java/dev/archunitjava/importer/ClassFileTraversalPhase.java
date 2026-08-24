package dev.archunitjava.importer;

/** The bounded reader phase in which a class resource failed. */
public enum ClassFileTraversalPhase {
    READ_BYTES,
    PARSE_HEADER,
    PARSE_MODEL,
    TRAVERSE_MODEL,
    ADAPT_MODEL
}
