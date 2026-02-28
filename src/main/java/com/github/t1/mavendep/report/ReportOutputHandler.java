package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.ProjectReport;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;

/// Handles formatting and outputting dependency reports.
public class ReportOutputHandler {

    private ReportOutputHandler() {
        // Utility class
    }

    public static void writeReport(List<ProjectReport> reports, ReportConfig config) {
        var filteredReports = config.showAll() ? reports : reports.stream().map(ProjectReport::onlyUpdates).toList();

        try {
            var out = (config.outputFile() == null) ? System.out : new PrintStream(config.outputFile());
            var reportWriter = switch (config.format()) {
                case json -> new JsonReportWriter(out, filteredReports);
                case text -> new TextReportWriter(out, filteredReports);
                case yaml -> new YamlReportWriter(out, filteredReports);
                case xml -> new XmlReportWriter(out, filteredReports);
            };
            reportWriter.run();
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }
}
