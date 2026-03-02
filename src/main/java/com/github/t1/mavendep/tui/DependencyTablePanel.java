package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.DependencyUpdate;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Renders the dependency/plugin table with selection checkboxes.
class DependencyTablePanel {

    private static final Style HEADER_STYLE = Style.EMPTY.bold().fg(Color.DARK_GRAY);

    private final TableState tableState = new TableState();

    public void render(Frame frame, Rect area, DashboardModel model) {
        var emptyMessage = model.emptyMessage();
        if (emptyMessage != null) {
            var block = Block.builder().borders(Borders.ALL).title(model.activeTab().name()).build();
            var paragraph = Paragraph.builder().text("\n  " + emptyMessage).block(block).build();
            frame.renderWidget(paragraph, area);
            return;
        }

        var grouped = model.activeGroupedUpdates();
        var multiPom = grouped.size() > 1;
        var rows = multiPom ? buildGroupedRows(grouped, model) : buildFlatRows(model.activeUpdates(), model);
        var visualIndex = multiPom ? toVisualIndex(model.cursor(), grouped) : model.cursor();
        tableState.select(visualIndex);

        var table = Table.builder()
                .header(Row.from("", "Group:Artifact", "", "Committed", "Current", "Latest", "Type")
                        .style(Style.EMPTY.bold()))
                .rows(rows)
                .widths(
                        Constraint.length(3),
                        Constraint.fill(),
                        Constraint.length(2),
                        Constraint.length(12),
                        Constraint.length(12),
                        Constraint.length(12),
                        Constraint.length(7))
                .highlightStyle(Style.EMPTY.bg(Color.DARK_GRAY))
                .highlightSymbol("> ")
                .block(Block.builder().borders(Borders.ALL).title(model.activeTab().name()).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private static List<Row> buildFlatRows(List<DependencyUpdate> updates, DashboardModel model) {
        return updates.stream().map(u -> toRow(u, model)).toList();
    }

    private static List<Row> buildGroupedRows(List<Map.Entry<Path, List<DependencyUpdate>>> grouped, DashboardModel model) {
        var rows = new ArrayList<Row>();
        for (var entry : grouped) {
            rows.add(toHeaderRow(entry.getKey()));
            for (var update : entry.getValue()) {
                rows.add(toRow(update, model));
            }
        }
        return rows;
    }

    private static final Path CWD = Path.of("").toAbsolutePath();

    private static Row toHeaderRow(Path pomPath) {
        var display = pomPath.isAbsolute() ? CWD.relativize(pomPath) : pomPath;
        return Row.from("", "── " + display + " ──", "", "", "", "", "")
                .style(HEADER_STYLE);
    }

    /// Maps a flat data index to a visual row index by adding the count of header rows above.
    static int toVisualIndex(int dataIndex, List<Map.Entry<Path, List<DependencyUpdate>>> grouped) {
        var headersAbove = 0;
        var remaining = dataIndex;
        for (var entry : grouped) {
            headersAbove++;
            var groupSize = entry.getValue().size();
            if (remaining < groupSize) return dataIndex + headersAbove;
            remaining -= groupSize;
        }
        return dataIndex + headersAbove;
    }

    private static Row toRow(DependencyUpdate update, DashboardModel model) {
        var effective = model.effectiveUpdate(update);
        var checkbox = !model.isChange(update) ? "   " : model.isSelected(update) ? "[x]" : "[ ]";
        var coords = String.valueOf(update.artifactRef());
        var icon = model.worstLogLevelFor(update.artifactRef())
                .map(level -> switch (level) {
                    case ERROR -> "❌";
                    case WARNING -> "⚠️";
                    case INFO -> "";
                })
                .orElse("");
        var committed = String.valueOf(model.committedVersion(update));
        var currentVersion = model.currentVersion(update);
        var current = String.valueOf(currentVersion);
        var latest = String.valueOf(update.latestVersion());
        var committedVer = model.committedVersion(update);
        var downgrade = committedVer != null && currentVersion != null
                && currentVersion.compareTo(committedVer) < 0;
        var type = (downgrade ? "-" : "") + effective.updateType().name();

        var style = switch (effective.updateType()) {
            case major -> Style.EMPTY.fg(Color.RED);
            case minor -> Style.EMPTY.fg(Color.YELLOW);
            case patch -> Style.EMPTY.fg(Color.GREEN);
            case none -> Style.EMPTY;
        };

        return Row.from(checkbox, coords, icon, committed, current, latest, type).style(style);
    }
}
