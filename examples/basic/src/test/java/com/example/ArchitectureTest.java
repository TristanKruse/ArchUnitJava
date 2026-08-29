package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.archunitjava.cli.CliExitCode;
import dev.archunitjava.cli.CliRunner;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
    @Test
    void compiledApplicationRespectsItsArchitecturePolicy() {
        Path root = Path.of("").toAbsolutePath().normalize();
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        int exit = new CliRunner().run(new String[] {
                "check",
                "--config", root.resolve("archunitjava.properties").toString(),
                "--root", root.toString(),
                "--result-format", "json"
        }, output, error);

        assertEquals(CliExitCode.SUCCESS.code(), exit, error.toString());
        assertTrue(output.toString().contains("\"status\":\"PASSED\""));
    }
}
