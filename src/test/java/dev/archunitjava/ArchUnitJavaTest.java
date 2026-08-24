package dev.archunitjava;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ArchUnitJavaTest {
    @Test
    void exposesScaffoldStatusWithoutClaimingImplementedFeatures() {
        assertEquals("scaffold", ArchUnitJava.status());
    }
}

