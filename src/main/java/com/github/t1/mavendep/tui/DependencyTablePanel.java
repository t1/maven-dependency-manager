package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
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
import java.util.function.Function;

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

        var widths = columnWidths(model);
        var table = Table.builder()
                .header(Row.from("", "Group:Artifact", "", "Scope", "Declared ", "Update")
                        .style(Style.EMPTY.bold()))
                .rows(rows)
                .widths(widths)
                .highlightStyle(Style.EMPTY.bg(Color.DARK_GRAY))
                .highlightSymbol("> ")
                .block(Block.builder().borders(Borders.ALL).title(model.activeTab().name()).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private static List<Row> buildFlatRows(List<Update> updates, DashboardModel model) {
        return updates.stream().map(u -> toRow(u, model)).toList();
    }

    private static List<Row> buildGroupedRows(List<Map.Entry<Path, List<Update>>> grouped, DashboardModel model) {
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
        return Row.from("", "── " + display + " ──", "", "", "", "")
                .style(HEADER_STYLE);
    }

    /// Maps a flat data index to a visual row index by adding the count of header rows above.
    static int toVisualIndex(int dataIndex, List<Map.Entry<Path, List<Update>>> grouped) {
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

    private static String formatShortScope(Update update) {
        var base = switch (update.type()) {
            case dependency -> update.scope().toString();
            case parent -> "parent";
            case plugin -> "plugin";
        };
        return update.profile() != null ? base + "@" + update.profile() : base;
    }

    private static Row toRow(Update update, DashboardModel model) {
        var checkbox = !model.isChange(update) ? "   " : model.isSelected(update) ? "[x]" : "[ ]";
        var coords = String.valueOf(update.artifactRef());
        var icon = model.worstLogLevelFor(update.artifactRef())
                .map(level -> switch (level) {
                    case ERROR -> "❌";
                    case WARNING -> "⚠️";
                    case INFO -> "";
                })
                .orElse("");
        var scope = formatShortScope(update);
        var declared = formatDeclared(update, model);
        var displayType = UpdateType.between(model.currentVersion(update), update.latestVersion());
        var style = styleFor(displayType, update.currentVersion() != null);

        return Row.from(checkbox, coords, icon, scope, declared, formatUpdate(update, model)).style(style);
    }

    private static Constraint[] columnWidths(DashboardModel model) {
        var updates = model.activeUpdates();
        return new Constraint[]{
                Constraint.length(maxLength(3, updates, update -> !model.isChange(update) ? "   " : model.isSelected(update) ? "[x]" : "[ ]")),
                Constraint.fill(),
                Constraint.length(maxLength(0, updates, update -> model.worstLogLevelFor(update.artifactRef())
                        .map(level -> switch (level) {
                            case ERROR -> "❌";
                            case WARNING -> "⚠️";
                            case INFO -> "";
                        })
                        .orElse(""))),
                Constraint.length(maxLength("Scope".length(), updates, DependencyTablePanel::formatShortScope)),
                Constraint.length(maxLength("Declared ".length(), updates, update -> formatDeclared(update, model))),
                Constraint.length(maxLength("Update".length(), updates, update -> formatUpdate(update, model)))
        };
    }

    private static int maxLength(int headerLength, List<Update> updates, Function<Update, String> formatter) {
        return Math.max(headerLength, updates.stream().map(formatter).mapToInt(String::length).max().orElse(0));
    }

    static String formatDeclared(Update update, DashboardModel model) {
        var committed = formatVersion(update.committedVersion(), null);
        var declared = formatVersion(model.declaredVersion(update), "<managed>");
        var formatted = (committed == null || committed.equals(declared)) ? declared : committed + " → " + declared;
        return formatted + " ";
    }

    static String formatUpdate(Update update, DashboardModel model) {
        var effective = formatVersion(model.currentVersion(update), "?");
        var latest = formatVersion(update.latestVersion(), "?");
        return effective.equals(latest) ? effective : effective + " → " + latest;
    }

    private static Style styleFor(UpdateType updateType, boolean resolved) {
        if (!resolved) return Style.EMPTY;
        return switch (updateType) {
            case major -> Style.EMPTY.fg(Color.RED);
            case minor -> Style.EMPTY.fg(Color.GREEN);
            case patch -> Style.EMPTY.fg(Color.BLUE);
            case none -> Style.EMPTY;
        };
    }

    private static String formatVersion(Version version, String fallback) {
        return version != null ? version.toString() : fallback;
    }
}
