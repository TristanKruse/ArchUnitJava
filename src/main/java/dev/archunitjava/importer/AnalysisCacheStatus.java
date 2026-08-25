package dev.archunitjava.importer;

/** Reviewable result of a safe cache lookup. */
public enum AnalysisCacheStatus {
    HIT,
    MISS_STORED,
    CORRUPT_REPLACED,
    FOREIGN_REPLACED,
    PARTIAL_REPLACED
}
