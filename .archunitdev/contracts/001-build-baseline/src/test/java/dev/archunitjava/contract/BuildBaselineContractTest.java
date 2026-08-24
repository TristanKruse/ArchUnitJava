package dev.archunitjava.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.ArchUnitJava;
import org.junit.jupiter.api.Test;

final class BuildBaselineContractTest {
    @Test
    void runsOnThePinnedJavaFeatureRelease() {
        assertEquals(25, Runtime.version().feature());
    }

    @Test
    void scaffoldDoesNotClaimUnimplementedProductFeatures() {
        assertEquals("scaffold", ArchUnitJava.status());
        assertTrue(ArchUnitJava.class.desiredAssertionStatus() || !ArchUnitJava.status().isBlank());
    }
}
