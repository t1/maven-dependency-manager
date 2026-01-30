package com.github.t1.mavendep.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class VerboseOptionIT extends BaseCliIT {

    @Test
    void shouldPrintStackTraceWithVerboseOption() throws IOException, InterruptedException {
        var invalidPomFile = tempDir.resolve("invalid.xml");
        writeString(invalidPomFile, "not a valid pom");

        var output = runCli(null, true, "check", invalidPomFile.toString(), "--verbose");

        then(output).contains("Warning: Can't parse POM file:");
        then(output).contains("at ");
    }

    @Test
    void shouldNotPrintStackTraceWithoutVerboseOption() throws IOException, InterruptedException {
        var invalidPomFile = tempDir.resolve("invalid.xml");
        writeString(invalidPomFile, "not a valid pom");

        var output = runCli(null, true, "check", invalidPomFile.toString());

        then(output).contains("Warning: Can't parse POM file:");
        then(output).doesNotContain("at ");
    }

    @Test
    void shouldSupportShortVerboseOption() throws IOException, InterruptedException {
        var invalidPomFile = tempDir.resolve("invalid.xml");
        writeString(invalidPomFile, "not a valid pom");

        var output = runCli(null, true, "check", invalidPomFile.toString(), "-v");

        then(output).contains("Warning: Can't parse POM file:");
        then(output).contains("at ");
    }
}
