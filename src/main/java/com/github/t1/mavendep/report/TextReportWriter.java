package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.DependencySummary;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Update;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Stream;

public class TextReportWriter implements ReportWriter {

    private final PrintStream out;
    private final List<ProjectReport> reports;

    public TextReportWriter(PrintStream out, List<ProjectReport> reports) {
        this.out = out;
        this.reports = reports;
    }

    @Override
    public void run() {
        printHeader();

        for (var report : reports) {
            printProjectReport(report, reports.size() > 1);
        }

        if (reports.size() > 1) {
            printTotalSummary();
        }
    }

    private void printHeader() {
        out.println();
        out.println(" ".repeat(40) + "==============================");
        out.println(" ".repeat(40) + "Maven Dependency Update Report");
        out.println(" ".repeat(40) + "==============================");
        out.println();
    }

    private void printProjectReport(ProjectReport report, boolean isMultiProject) {
        if (isMultiProject) out.println("-".repeat(120));
        out.println("  Project: " + report.pom().path().toAbsolutePath());

        var updates = Stream.concat(
                        Stream.concat(
                                report.parentUpdate().stream(),
                                report.dependencyUpdates().stream()),
                        report.pluginUpdates().stream())
                .toList();
        if (!updates.isEmpty()) {
            printReportWithUpdates(updates);
        }

        var summary = DependencySummary.summarize(report);
        printSummaryText(summary, "  Summary: ");
        out.println();
    }

    private void printReportWithUpdates(List<Update> updates) {
        var hasCommitted = updates.stream().anyMatch(u -> u.committedVersion() != null);
        var widths = calculateColumnWidths(updates, hasCommitted);

        printTableBorder(widths, "┌─", "─┬─", "─┐");
        if (hasCommitted) {
            printTableRow(widths, "Scope", "Group ID", "Artifact ID", "Committed", "Effective", "Latest", "Update");
        } else {
            printTableRow(widths, "Scope", "Group ID", "Artifact ID", "Effective", "Latest", "Update");
        }
        printTableBorder(widths, "├─", "─┼─", "─┤");
        printTableRows(updates, widths, hasCommitted);
        printTableBorder(widths, "└─", "─┴─", "─┘");
    }

    private int[] calculateColumnWidths(List<Update> updates, boolean hasCommitted) {
        var scopeWidth = "Scope".length();
        var groupWidth = "Group ID".length();
        var depWidth = "Artifact ID".length();
        var committedWidth = hasCommitted ? "Committed".length() : 0;
        var curWidth = "Effective".length();
        var latWidth = "Latest".length();
        var updateWidth = "Update".length();

        for (var update : updates) {
            scopeWidth = Math.max(scopeWidth, update.formatScope().length());
            groupWidth = Math.max(groupWidth, update.groupId().length());
            depWidth = Math.max(depWidth, update.artifactId().length());
            if (hasCommitted) committedWidth = Math.max(committedWidth, formatCommittedVersion(update).length());
            curWidth = Math.max(curWidth, formatCurrentVersion(update).length());
            latWidth = Math.max(latWidth, formatLatestVersion(update).length());
            updateWidth = Math.max(updateWidth, formatUpdateType(update).length());
        }

        if (hasCommitted) {
            return new int[]{scopeWidth, groupWidth, depWidth, committedWidth, curWidth, latWidth, updateWidth};
        }
        return new int[]{scopeWidth, groupWidth, depWidth, curWidth, latWidth, updateWidth};
    }

    private void printTableRows(List<Update> updates, int[] widths, boolean hasCommitted) {
        for (var update : updates) {
            if (hasCommitted) {
                printTableRow(widths, update.formatScope(),
                        update.groupId(),
                        update.artifactId(),
                        formatCommittedVersion(update),
                        formatCurrentVersion(update),
                        formatLatestVersion(update),
                        formatUpdateType(update));
            } else {
                printTableRow(widths, update.formatScope(),
                        update.groupId(),
                        update.artifactId(),
                        formatCurrentVersion(update),
                        formatLatestVersion(update),
                        formatUpdateType(update));
            }
        }
    }

    private String formatCommittedVersion(Update update) {
        return update.committedVersion() != null ? update.committedVersion().toString() : "";
    }

    private String formatCurrentVersion(Update update) {
        return update.currentVersion() != null ? update.currentVersion().toString() : "<managed>";
    }

    private String formatLatestVersion(Update update) {
        return update.latestVersion() != null ? update.latestVersion().toString() : "?";
    }

    private String formatUpdateType(Update update) {
        return update.currentVersion() != null ? update.updateType().toString() : "";
    }

    private void printTotalSummary() {
        var totalSummary = DependencySummary.summarize(reports);
        if (totalSummary.totalDependencies() > 0) {
            out.println("=".repeat(120));
            printSummaryText(totalSummary, "  Total Summary: ");
            out.println();
        }
    }

    private void printSummaryText(DependencySummary summary, String prefix) {
        out.print(prefix);
        out.print(summary.totalDependencies());
        out.print(summary.totalDependencies() == 1 ? " dependency, " : " dependencies, ");
        out.print(summary.outdatedDependencies() + " updates available (");
        out.print(summary.majorUpdates() + " major, ");
        out.print(summary.minorUpdates() + " minor, ");
        out.println(summary.patchUpdates() + " patch)");
    }

    private void printTableBorder(int[] widths, String left, String middle, String right) {
        out.print(left);
        for (var i = 0; i < widths.length; i++) {
            out.print("─".repeat(widths[i]));
            if (i < widths.length - 1) {
                out.print(middle);
            }
        }
        out.println(right);
    }

    private void printTableRow(int[] widths, String... cells) {
        out.print("│ ");
        for (var i = 0; i < cells.length; i++) {
            out.print(TableFormat.padRight(cells[i], widths[i]));
            out.print(" │");
            if (i < cells.length - 1) {
                out.print(" ");
            }
        }
        out.println();
    }

}
