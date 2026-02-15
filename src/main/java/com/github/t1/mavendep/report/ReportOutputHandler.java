package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.OutputFormat;
import com.github.t1.mavendep.domain.ProjectReport;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;

/// Handles formatting and outputting dependency reports.
public class ReportOutputHandler {

    private ReportOutputHandler() {
        // Utility class
    }

    public static void writeReport(List<ProjectReport> reports, OutputFormat format, String outputFile, boolean showAll) {
        var filteredReports = showAll ? reports : reports.stream().map(ProjectReport::onlyUpdates).toList();

        try {
            var out = (outputFile == null) ? System.out : new PrintStream(outputFile);
            var reportWriter = switch (format) {
                case json -> new JsonReportWriter(out, filteredReports);
                case text -> new TextReportWriter(out, filteredReports);
            };
            reportWriter.run();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
