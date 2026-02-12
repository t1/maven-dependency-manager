package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.OutputFormat;
import com.github.t1.mavendep.domain.ProjectReport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.writeString;

/// Handles formatting and outputting dependency reports.
public class ReportOutputHandler {

    private ReportOutputHandler() {
        // Utility class
    }

    public static void writeReport(List<ProjectReport> reports, OutputFormat format, String outputFile, boolean showAll) {
        var filteredReports = showAll ? reports : reports.stream().map(ProjectReport::onlyUpdates).toList();
        var output = switch (format) {
            case json -> new JsonReportWriter().write(filteredReports);
            case text -> new TextReportWriter().write(filteredReports);
        };

        if (outputFile != null) {
            try {
                writeString(Path.of(outputFile), output);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println(output);
        }
    }
}
