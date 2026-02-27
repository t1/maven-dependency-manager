package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.DependencySummary;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.ProjectReport;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;

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

    private void printReportWithUpdates(List<DependencyUpdate> updates) {
        var widths = calculateColumnWidths(updates);

        printTableBorder(widths, "┌─", "─┬─", "─┐");
        printTableRow(widths, "Type/Scope", "Group ID", "Artifact ID", "Current", "Latest", "Update");
        printTableBorder(widths, "├─", "─┼─", "─┤");
        printTableRows(updates, widths);
        printTableBorder(widths, "└─", "─┴─", "─┘");
    }

    private int[] calculateColumnWidths(List<DependencyUpdate> updates) {
        var typeScopeWidth = "Type/Scope".length();
        var groupWidth = "Group ID".length();
        var depWidth = "Artifact ID".length();
        var curWidth = "Current".length();
        var latWidth = "Latest".length();
        var updateWidth = "Update".length();

        for (var update : updates) {
            typeScopeWidth = Math.max(typeScopeWidth, formatTypeScope(update).length());
            groupWidth = Math.max(groupWidth, update.groupId().length());
            depWidth = Math.max(depWidth, update.artifactId().length());
            curWidth = Math.max(curWidth, formatCurrentVersion(update).length());
            latWidth = Math.max(latWidth, formatLatestVersion(update).length());
            updateWidth = Math.max(updateWidth, update.updateType().toString().length());
        }

        return new int[]{typeScopeWidth, groupWidth, depWidth, curWidth, latWidth, updateWidth};
    }

    private void printTableRows(List<DependencyUpdate> updates, int[] widths) {
        for (var update : updates) {
            printTableRow(widths, formatTypeScope(update),
                    update.groupId(),
                    update.artifactId(),
                    formatCurrentVersion(update),
                    formatLatestVersion(update),
                    update.updateType().toString());
        }
    }

    private String formatCurrentVersion(DependencyUpdate update) {
        return update.currentVersion() != null ? update.currentVersion().toString() : "<managed>";
    }

    private String formatLatestVersion(DependencyUpdate update) {
        return update.latestVersion() != null ? update.latestVersion().toString() : "?";
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
            out.print(padRight(cells[i], widths[i]));
            out.print(" │");
            if (i < cells.length - 1) {
                out.print(" ");
            }
        }
        out.println();
    }

    private String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    private String formatTypeScope(DependencyUpdate update) {
        if (update.type() == dependency) {
            return "dependency/" + update.scope();
        }
        return update.type().toString();
    }
}
