package com.github.t1.mavendep.report;

import com.github.t1.mavendep.domain.Version;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Formats a list of versions as a table tree grouped by major and minor version.
///
/// The header row shows "Major", "Minor", and "Version" (spanning all version columns).
/// The major version column spans all rows for that major version.
/// The minor version column spans all rows for that minor group.
/// Version strings are laid out in columns; if the table exceeds 120 characters,
/// versions wrap to the next row.
///
/// Example output:
/// ```
/// ┌───────┬───────┬─────────┬─────────┐
/// │ Major │ Minor │ Version            │
/// ├───────┼───────┼─────────┬─────────┤
/// │ 3     │ 25    │ 3.25.1  │ 3.25.2  │
/// │       │ 26    │ 3.26.0  │         │
/// └───────┴───────┴─────────┴─────────┘
/// ```
public class VersionTreeFormatter {

    private static final int MAX_TABLE_WIDTH = 120;
    private static final String MAJOR_HEADER = "Major";
    private static final String MINOR_HEADER = "Minor";
    private static final String VERSION_HEADER = "Version";
    /// Each column adds `│` + space + content + space = content width + 3
    private static final int COLUMN_OVERHEAD = 3;

    public static String format(List<Version> versions) {
        if (versions.isEmpty()) return "";
        return new VersionTreeFormatter(versions).buildTable();
    }

    private final LinkedHashMap<Integer, LinkedHashMap<Integer, List<Version>>> majorGroups;
    private final int[] columnWidths;
    private final int versionColumns;
    private final int versionWidth;

    private VersionTreeFormatter(List<Version> versions) {
        var majorWidth = Math.max(
                versions.stream().mapToInt(v -> String.valueOf(v.major()).length()).max().orElse(1),
                MAJOR_HEADER.length());
        var minorWidth = Math.max(
                versions.stream().mapToInt(v -> String.valueOf(v.minor()).length()).max().orElse(1),
                MINOR_HEADER.length());
        this.versionWidth = Math.max(
                versions.stream().mapToInt(v -> v.toString().length()).max().orElse(1),
                VERSION_HEADER.length());
        this.majorGroups = groupByMajorMinor(versions);
        this.versionColumns = calculateVersionColumns(majorWidth, minorWidth);
        this.columnWidths = buildColumnWidths(majorWidth, minorWidth);
    }

    private int calculateVersionColumns(int majorWidth, int minorWidth) {
        var fixedWidth = 1 + majorWidth + minorWidth + 2 * COLUMN_OVERHEAD;
        var maxColumns = (MAX_TABLE_WIDTH - fixedWidth) / (versionWidth + COLUMN_OVERHEAD);
        var maxPatchesPerGroup = majorGroups.values().stream()
                .flatMap(minors -> minors.values().stream())
                .mapToInt(List::size)
                .max().orElse(1);
        return Math.max(1, Math.min(maxPatchesPerGroup, maxColumns));
    }

    private int[] buildColumnWidths(int majorWidth, int minorWidth) {
        var widths = new int[2 + versionColumns];
        widths[0] = majorWidth;
        widths[1] = minorWidth;
        for (var i = 2; i < widths.length; i++) widths[i] = versionWidth;
        return widths;
    }

    private String buildTable() {
        var output = new StringBuilder();
        appendHeader(output);
        var majorEntries = new ArrayList<>(majorGroups.entrySet());
        for (var majorIndex = 0; majorIndex < majorEntries.size(); majorIndex++) {
            if (majorIndex > 0) output.append(border("├─", "─┼─", "─┤"));
            appendMajorGroup(output, majorEntries.get(majorIndex));
        }
        output.append(border("└─", "─┴─", "─┘"));
        return output.toString();
    }

    private void appendHeader(StringBuilder output) {
        var spanWidth = versionColumns * versionWidth + (versionColumns - 1) * COLUMN_OVERHEAD;
        output.append(border("┌─", "─┬─", "─┐", new int[]{columnWidths[0], columnWidths[1], spanWidth}));
        output.append("│ ").append(padRight(MAJOR_HEADER, columnWidths[0])).append(" │ ");
        output.append(padRight(MINOR_HEADER, columnWidths[1])).append(" │ ");
        output.append(padRight(VERSION_HEADER, spanWidth)).append(" │\n");
        output.append(headerSeparator());
    }

    private String headerSeparator() {
        var sb = new StringBuilder();
        sb.append("├─");
        for (var i = 0; i < columnWidths.length; i++) {
            sb.append("─".repeat(columnWidths[i]));
            if (i < columnWidths.length - 1) {
                // ┼ for boundaries that exist in both header and data (Major/Minor columns)
                // ┬ for boundaries that only exist in data (between version subcolumns)
                sb.append(i < 2 ? "─┼─" : "─┬─");
            } else {
                sb.append("─┤");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private void appendMajorGroup(StringBuilder output,
                                  Map.Entry<Integer, LinkedHashMap<Integer, List<Version>>> majorEntry) {
        var minorEntries = new ArrayList<>(majorEntry.getValue().entrySet());
        for (var minorIndex = 0; minorIndex < minorEntries.size(); minorIndex++) {
            var minorEntry = minorEntries.get(minorIndex);
            var majorCell = (minorIndex == 0) ? String.valueOf(majorEntry.getKey()) : "";
            appendMinorGroup(output, majorCell, minorEntry.getKey(), minorEntry.getValue());
        }
    }

    private void appendMinorGroup(StringBuilder output, String majorCell, int minor, List<Version> versions) {
        var rows = partitionIntoRows(versions);
        for (var rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            var minorCell = (rowIndex == 0) ? String.valueOf(minor) : "";
            var effectiveMajor = (rowIndex == 0) ? majorCell : "";
            output.append(row(effectiveMajor, minorCell, rows.get(rowIndex)));
        }
    }

    private List<List<Version>> partitionIntoRows(List<Version> versions) {
        var rows = new ArrayList<List<Version>>();
        for (var i = 0; i < versions.size(); i += versionColumns) {
            rows.add(versions.subList(i, Math.min(i + versionColumns, versions.size())));
        }
        return rows;
    }

    private static LinkedHashMap<Integer, LinkedHashMap<Integer, List<Version>>> groupByMajorMinor(
            List<Version> versions) {
        var result = new LinkedHashMap<Integer, LinkedHashMap<Integer, List<Version>>>();
        for (var version : versions) {
            result.computeIfAbsent(version.major(), _ -> new LinkedHashMap<>())
                    .computeIfAbsent(version.minor(), _ -> new ArrayList<>())
                    .add(version);
        }
        return result;
    }

    private String border(String left, String middle, String right) {
        return border(left, middle, right, columnWidths);
    }

    private static String border(String left, String middle, String right, int[] widths) {
        var sb = new StringBuilder();
        sb.append(left);
        for (var i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i]));
            sb.append(i < widths.length - 1 ? middle : right);
        }
        sb.append("\n");
        return sb.toString();
    }

    private String row(String majorCell, String minorCell, List<Version> versions) {
        var sb = new StringBuilder();
        sb.append("│ ").append(padRight(majorCell, columnWidths[0])).append(" │ ");
        sb.append(padRight(minorCell, columnWidths[1])).append(" │");
        for (var i = 0; i < versionColumns; i++) {
            sb.append(" ");
            var cell = i < versions.size() ? versions.get(i).toString() : "";
            sb.append(padRight(cell, columnWidths[2 + i]));
            sb.append(" │");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }
}
