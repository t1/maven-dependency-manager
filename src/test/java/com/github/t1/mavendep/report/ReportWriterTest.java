package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.BDDAssertions.then;

class ReportWriterTest {
    private static final Pom DUMMY_POM = Pom.parse(Path.of("pom.xml")).orElseThrow();

    // Test list for ReportWriter interface contract

    @Test
    void shouldDefineRunMethodThatWritesToPrintStream() {
        var buffer = new ByteArrayOutputStream();
        ReportWriter writer = () -> new PrintStream(buffer).print("test");

        writer.run();

        then(buffer.toString()).isEqualTo("test");
    }

    @Test
    void shouldAllowJsonReportWriterToImplementInterface() {
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(), List.of(), 0);
        var buffer = new ByteArrayOutputStream();

        ReportWriter writer = new JsonReportWriter(new PrintStream(buffer), List.of(report));
        writer.run();

        then(buffer.toString()).contains("projects");
    }

    @Test
    void shouldAllowTextReportWriterToImplementInterface() {
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(), List.of(), 0);
        var buffer = new ByteArrayOutputStream();

        ReportWriter writer = new TextReportWriter(new PrintStream(buffer), List.of(report));
        writer.run();

        then(buffer.toString()).contains("Maven Dependency Update Report");
    }
}
