package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.BDDAssertions.then;

class ReportWriterTest {
    private static final Pom DUMMY_POM = Pom.parse(Path.of("pom.xml")).orElseThrow();

    // Test list for ReportWriter interface contract

    @Test
    void shouldDefineWriteMethodThatAcceptsListOfProjectReports() {
        ReportWriter writer = (_) -> "";

        var result = writer.write(List.of());

        then(result).isNotNull();
    }

    @Test
    void shouldAllowJsonReportWriterToImplementInterface() {
        ReportWriter writer = new JsonReportWriter();
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(), List.of(), 0);

        var result = writer.write(List.of(report));

        then(result).contains("projects");
    }

    @Test
    void shouldAllowTextReportWriterToImplementInterface() {
        ReportWriter writer = new TextReportWriter();
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(), List.of(), 0);

        var result = writer.write(List.of(report));

        then(result).contains("Maven Dependency Update Report");
    }
}
