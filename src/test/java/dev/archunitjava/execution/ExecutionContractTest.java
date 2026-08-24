package dev.archunitjava.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ExecutionContractTest {
    @Test
    void optionsHaveStrictDefaultsAndBuilderSnapshotsAreImmutable() {
        CheckOptions defaults = CheckOptions.defaults();
        CheckOptions.Builder builder = CheckOptions.builder().allowEmptySelection(true);
        CheckOptions first = builder.build();
        builder.allowIncompleteAnalysis(true).allowEmptySelection(false);
        CheckOptions second = builder.build();

        assertFalse(defaults.allowEmptySelection());
        assertFalse(defaults.allowIncompleteAnalysis());
        assertTrue(first.allowEmptySelection());
        assertFalse(first.allowIncompleteAnalysis());
        assertFalse(second.allowEmptySelection());
        assertTrue(second.allowIncompleteAnalysis());
        assertEquals(first, first.toBuilder().build());
        assertNotEquals(first, second);
    }

    @Test
    void ruleFailuresAreReturnedAsValuesAndDefaultsReachEveryTerminal() {
        AtomicReference<CheckOptions> observed = new AtomicReference<>();
        Checkable<List<RuleFailureValue>> rule = options -> {
            observed.set(options);
            return List.of(new RuleFailureValue("forbidden-dependency"));
        };

        assertEquals(List.of(new RuleFailureValue("forbidden-dependency")), rule.check());
        assertSame(CheckOptions.defaults(), observed.get());

        CheckOptions permissive = CheckOptions.builder().allowEmptySelection(true).build();
        rule.check(permissive);
        assertSame(permissive, observed.get());
    }

    @Test
    void technicalAndUserErrorsRetainCausesAndSortedImmutableContext() {
        IOException cause = new IOException("unreadable class file");
        LinkedHashMap<String, String> mutableContext = new LinkedHashMap<>();
        mutableContext.put("path", "classes/Broken.class");
        mutableContext.put("entry", "application.jar");
        TechnicalError technical = new TechnicalError(
                "classfile.read", "Could not read a class file", cause, mutableContext);
        mutableContext.put("later", "must not leak");

        assertEquals("classfile.read", technical.code());
        assertSame(cause, technical.getCause());
        assertEquals(List.of("entry", "path"), List.copyOf(technical.context().keySet()));
        assertFalse(technical.context().containsKey("later"));
        assertThrows(UnsupportedOperationException.class,
                () -> technical.context().put("other", "value"));

        UserError user = new UserError(
                "pattern.invalid", "Fix the supplied pattern", cause, Map.of("pattern", "["));
        assertSame(cause, user.getCause());
        assertEquals("[", user.context().get("pattern"));

        UserError specialized = new SpecializedUserError("Use a qualified name");
        assertEquals("name.invalid", specialized.code());
    }

    @Test
    void errorsRejectMissingActionableMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new UserError(" ", "message"));
        assertThrows(IllegalArgumentException.class, () -> new TechnicalError("code", " "));
        assertThrows(IllegalArgumentException.class, () -> new UserError(
                "code", "message", null, Map.of("path", " ")));
    }

    private record RuleFailureValue(String code) {}

    private static final class SpecializedUserError extends UserError {
        private SpecializedUserError(String message) {
            super("name.invalid", message);
        }
    }
}
