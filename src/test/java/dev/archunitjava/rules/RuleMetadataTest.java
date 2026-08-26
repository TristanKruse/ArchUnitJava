package dev.archunitjava.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.archunitjava.result.RuleMetadata;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleMetadataTest {
    @Test
    void metadataDecorationReturnsNewRulesAndFlowsIntoResults() {
        ArchitectureRule base = passingRule(
                "dependencies:no-ui-to-db:v1", "UI must not depend on the database");
        ArchitectureRule decorated = base
                .as("UI isolation")
                .because("the database is an infrastructure detail")
                .tagged("critical", "architecture")
                .withSeverity(Severity.WARNING);

        RuleResult result = decorated.check();

        assertEquals("UI must not depend on the database", base.metadata().displayName());
        assertEquals("UI isolation", result.metadata().displayName());
        assertEquals("the database is an infrastructure detail",
                result.metadata().rationale().orElseThrow());
        assertEquals(List.of("architecture", "critical"), result.metadata().tags());
        assertEquals(Severity.WARNING, result.metadata().severity());
        assertEquals(
                "UI isolation, because the database is an infrastructure detail",
                result.metadata().humanDescription());
    }

    @Test
    void semanticAndPresentationIdentitiesChangeIndependently() {
        ArchitectureRule base = passingRule("layers:v1", "layers are respected");
        ArchitectureRule renamed = base.as("layer boundaries");
        ArchitectureRule retaggedInDifferentOrder = base.tagged("two", "one");
        ArchitectureRule sameTags = base.tagged(List.of("one", "two"));
        ArchitectureRule semanticChange = passingRule("layers:v2", "layers are respected");

        assertEquals(base.metadata().semanticIdentity(), renamed.metadata().semanticIdentity());
        assertNotEquals(
                base.metadata().presentationIdentity(), renamed.metadata().presentationIdentity());
        assertEquals(retaggedInDifferentOrder.metadata(), sameTags.metadata());
        assertNotEquals(
                base.metadata().semanticIdentity(), semanticChange.metadata().semanticIdentity());
        assertNotEquals(
                base.metadata().presentationIdentity(), semanticChange.metadata().presentationIdentity());
    }

    @Test
    void evaluationCannotReplaceOrDropTheSuppliedMetadata() {
        ArchitectureRule invalid = ArchitectureRules.define(
                "expected", "expected rule",
                (metadata, options) -> RuleResult.passed("different"));

        assertThrows(IllegalStateException.class, invalid::check);
        assertThrows(IllegalArgumentException.class,
                () -> new RuleMetadata(
                        "rule", "0".repeat(64), "name", "description",
                        java.util.Optional.empty(), List.of(), Severity.ERROR));
    }

    private static ArchitectureRule passingRule(String identity, String description) {
        return ArchitectureRules.define(
                identity, description, (metadata, options) -> RuleResult.passed(metadata));
    }
}
