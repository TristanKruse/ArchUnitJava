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
    void exposesTheExpectedProductNamespace() {
        assertEquals("dev.archunitjava", ArchUnitJava.class.getPackageName());
        assertTrue(ArchUnitJava.class.getSimpleName().startsWith("ArchUnit"));
    }
}
