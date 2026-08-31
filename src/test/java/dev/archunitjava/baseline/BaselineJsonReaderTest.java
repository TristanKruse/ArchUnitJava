package dev.archunitjava.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.archunitjava.graph.DependencyEvidence;
import dev.archunitjava.graph.LocationId;
import dev.archunitjava.graph.TypeId;
import dev.archunitjava.report.ResultReport;
import dev.archunitjava.result.RuleResult;
import dev.archunitjava.result.Severity;
import dev.archunitjava.result.Violation;
import dev.archunitjava.result.ViolationId;
import dev.archunitjava.result.ViolationSubject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BaselineJsonReaderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void canonicalJsonRoundTripsAcrossBytesTextAndFiles() throws IOException {
        ReviewedBaseline expected = baseline();
        String json = BaselineJsonRenderer.render(expected);
        Path file = temporaryDirectory.resolve("architecture-baseline.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);

        assertEquals(expected, BaselineJsonReader.read(json));
        assertEquals(expected, BaselineJsonReader.read(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, BaselineJsonReader.read(file));
        assertEquals(expected, BaselineJsonReader.read(file, BaselineReadLimits.defaults()));
        assertEquals(json, BaselineJsonRenderer.render(BaselineJsonReader.read(json)));
    }

    @Test
    void rejectsDuplicateUnknownMissingAndTrailingFields() {
        String json = BaselineJsonRenderer.render(baseline());

        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(
                json.replaceFirst("\\{", "{\"schemaVersion\":\"archunitjava.baseline.v1\",")));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(
                json.replaceFirst("\\{", "{\"unknown\":null,")));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(
                json.replaceFirst("\"schemaVersion\":\"archunitjava.baseline.v1\",", "")));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(json + "false"));
    }

    @Test
    void rejectsInvalidEncodingUnicodeDatesAndFingerprintTampering() {
        String json = BaselineJsonRenderer.render(baseline());

        assertThrows(BaselineFormatException.class,
                () -> BaselineJsonReader.read(new byte[] {(byte) 0xc3, 0x28}));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(
                json.replace("Known debt", "\\ud800")));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(
                json.replace("2026-09-30", "2026-02-30")));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(
                json.replaceFirst("[0-9a-f]{64}", "0".repeat(64))));
    }

    @Test
    void enforcesByteDepthCountAndStringLimitsBeforeDomainConstruction() {
        String json = BaselineJsonRenderer.render(baseline());
        BaselineReadLimits byteLimit = new BaselineReadLimits(
                10, 16, 10, 10, 100, 1000);
        BaselineReadLimits depthLimit = new BaselineReadLimits(
                1_000_000, 2, 10, 10, 100, 1000);
        BaselineReadLimits stringLimit = new BaselineReadLimits(
                1_000_000, 16, 10, 10, 100, 3);
        String finding = json.substring(
                json.indexOf("\"findings\":[") + "\"findings\":[".length(),
                json.indexOf("],\"suppressions\":"));
        String twoFindings = json.replace(finding, finding + "," + finding);
        BaselineReadLimits findingLimit = new BaselineReadLimits(
                1_000_000, 16, 1, 10, 100, 1000);
        String suppression = json.substring(
                json.indexOf("\"suppressions\":[") + "\"suppressions\":[".length(),
                json.lastIndexOf("]}"));
        String twoSuppressions = json.replace(suppression, suppression + "," + suppression);
        BaselineReadLimits suppressionLimit = new BaselineReadLimits(
                1_000_000, 16, 10, 1, 100, 1000);

        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(json, byteLimit));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(json, depthLimit));
        assertThrows(BaselineFormatException.class, () -> BaselineJsonReader.read(json, stringLimit));
        assertThrows(BaselineFormatException.class,
                () -> BaselineJsonReader.read(twoFindings, findingLimit));
        assertThrows(BaselineFormatException.class,
                () -> BaselineJsonReader.read(twoSuppressions, suppressionLimit));
    }

    private static ReviewedBaseline baseline() {
        Violation violation = new Violation(
                new ViolationId("bad-dependency"), "dependency.forbidden", Severity.ERROR,
                List.of(new ViolationSubject("origin", TypeId.ofBinaryName("example.Bad"))),
                List.of(DependencyEvidence.at(LocationId.ofResourcePath("classes/example/Bad.class"))),
                Map.of("policy", "boundary"));
        ReviewedBaseline frozen = BaselineCommands.freeze(ResultReport.of(List.of(
                RuleResult.failed("rule", List.of(violation), List.of()))));
        Suppression suppression = Suppression.forRule(
                "migration", "Known debt", "rule", Optional.of(LocalDate.of(2026, 9, 30)));
        return ReviewedBaseline.of(frozen.findings(), List.of(suppression));
    }
}
