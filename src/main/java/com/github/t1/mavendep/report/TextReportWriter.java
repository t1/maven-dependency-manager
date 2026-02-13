package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.DependencySummary;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.ProjectReport;

import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;

public class TextReportWriter implements ReportWriter {

    @Override
    public String write(List<ProjectReport> reports) {
        var sb = new StringBuilder();

        appendHeader(sb);

        for (var report : reports) {
            appendProjectReport(sb, report, reports.size() > 1);
        }

        if (reports.size() > 1) {
            appendTotalSummary(sb, reports);
        }

        return sb.toString();
    }

    private void appendHeader(StringBuilder sb) {
        sb.append("\n");
        sb.append(" ".repeat(40)).append("==============================\n");
        sb.append(" ".repeat(40)).append("Maven Dependency Update Report\n");
        sb.append(" ".repeat(40)).append("==============================\n\n");
    }

    private void appendProjectReport(StringBuilder sb, ProjectReport report, boolean isMultiProject) {
        if (isMultiProject) sb.append("-".repeat(120)).append("\n");
        sb.append("  Project: ").append(report.pom().path().toAbsolutePath()).append("\n");

        var updates = Stream.concat(
                        Stream.concat(
                                report.parentUpdate().stream(),
                                report.dependencyUpdates().stream()),
                        report.pluginUpdates().stream())
                .toList();
        if (!updates.isEmpty()) {
            appendReportWithUpdates(sb, updates);
        }

        var summary = DependencySummary.from(report);
        appendSummaryText(sb, summary, "  Summary: ");
        sb.append("\n");
    }

    private void appendReportWithUpdates(StringBuilder sb, List<DependencyUpdate> updates) {
        var widths = calculateColumnWidths(updates);

        appendTableBorder(sb, widths, "┌─", "─┬─", "─┐");
        appendTableRow(sb, widths, "Type/Scope", "Group ID", "Artifact ID", "Current", "Latest", "Update");
        appendTableBorder(sb, widths, "├─", "─┼─", "─┤");
        appendTableRows(sb, updates, widths);
        appendTableBorder(sb, widths, "└─", "─┴─", "─┘");
    }

    private int[] calculateColumnWidths(List<DependencyUpdate> updates) {
        var typeScopeWidth = "Type/Scope".length();
        var groupWidth = "Group ID".length();
        var depWidth = "Artifact ID".length();
        var curWidth = "Current".length();
        var latWidth = "Latest".length();
        var updateWidth = "Update".length();

        for (var update : updates) {
            var typeScopeText = formatTypeScope(update);
            typeScopeWidth = Math.max(typeScopeWidth, typeScopeText.length());
            groupWidth = Math.max(groupWidth, update.groupId().length());
            depWidth = Math.max(depWidth, update.artifactId().length());
            var currentVersionStr = update.currentVersion() != null ? update.currentVersion().toString() : "<managed>";
            var latestVersionStr = update.latestVersion() != null ? update.latestVersion().toString() : "?";
            curWidth = Math.max(curWidth, currentVersionStr.length());
            latWidth = Math.max(latWidth, latestVersionStr.length());
            updateWidth = Math.max(updateWidth, update.updateType().toString().length());
        }

        return new int[]{typeScopeWidth, groupWidth, depWidth, curWidth, latWidth, updateWidth};
    }

    private void appendTableRows(StringBuilder sb, List<DependencyUpdate> updates, int[] widths) {
        for (var update : updates) {
            var typeScopeText = formatTypeScope(update);
            var currentVersionStr = update.currentVersion() != null ? update.currentVersion().toString() : "<managed>";
            var latestVersionStr = update.latestVersion() != null ? update.latestVersion().toString() : "?";
            appendTableRow(sb, widths, typeScopeText,
                    update.groupId(),
                    update.artifactId(),
                    currentVersionStr,
                    latestVersionStr,
                    update.updateType().toString());
        }
    }

    private void appendTotalSummary(StringBuilder sb, List<ProjectReport> reports) {
        var totalSummary = DependencySummary.from(reports);
        if (totalSummary.totalDependencies() > 0) {
            sb.append("=".repeat(120)).append("\n");
            appendSummaryText(sb, totalSummary, "  Total Summary: ");
            sb.append("\n");
        }
    }

    private void appendSummaryText(StringBuilder sb, DependencySummary summary, String prefix) {
        sb.append(prefix);
        sb.append(summary.totalDependencies());
        sb.append(summary.totalDependencies() == 1 ? " dependency, " : " dependencies, ");
        sb.append(summary.outdatedDependencies()).append(" updates available (");
        sb.append(summary.majorUpdates()).append(" major, ");
        sb.append(summary.minorUpdates()).append(" minor, ");
        sb.append(summary.patchUpdates()).append(" patch");
        sb.append(")\n");
    }

    private void appendTableBorder(StringBuilder sb, int[] widths, String left, String middle, String right) {
        sb.append(left);
        for (var i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i]));
            if (i < widths.length - 1) {
                sb.append(middle);
            }
        }
        sb.append(right).append("\n");
    }

    private void appendTableRow(StringBuilder sb, int[] widths, String... cells) {
        sb.append("│ ");
        for (var i = 0; i < cells.length; i++) {
            sb.append(padRight(cells[i], widths[i]));
            sb.append(" │");
            if (i < cells.length - 1) {
                sb.append(" ");
            }
        }
        sb.append("\n");
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
