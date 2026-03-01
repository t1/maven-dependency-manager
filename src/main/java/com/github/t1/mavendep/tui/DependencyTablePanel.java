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

/// Renders the dependency/plugin table with selection checkboxes.
class DependencyTablePanel {

    private final TableState tableState = new TableState();

    public void render(Frame frame, Rect area, DashboardModel model) {
        var emptyMessage = model.emptyMessage();
        if (emptyMessage != null) {
            var block = Block.builder().borders(Borders.ALL).title(model.activeTab().name()).build();
            var paragraph = Paragraph.builder().text("\n  " + emptyMessage).block(block).build();
            frame.renderWidget(paragraph, area);
            return;
        }

        var updates = model.activeUpdates();
        tableState.select(model.cursor());

        var rows = updates.stream()
                .map(u -> toRow(u, model))
                .toList();

        var table = Table.builder()
                .header(Row.from("", "Group:Artifact", "", "Was", "Is", "Latest", "Type")
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

    private static Row toRow(DependencyUpdate update, DashboardModel model) {
        var effective = model.effectiveUpdate(update);
        var checkbox = !update.isChange() ? "   " : model.isSelected(update) ? "[x]" : "[ ]";
        var coords = String.valueOf(update.artifactRef());
        var icon = model.worstLogLevelFor(update.artifactRef())
                .map(level -> switch (level) {
                    case ERROR -> "❌";
                    case WARNING -> "⚠️";
                    case INFO -> "";
                })
                .orElse("");
        var was = String.valueOf(update.currentVersion());
        var isVersion = model.isVersion(update);
        var is = String.valueOf(isVersion);
        var latest = String.valueOf(update.latestVersion());
        var downgrade = update.currentVersion() != null && isVersion != null
                && isVersion.compareTo(update.currentVersion()) < 0;
        var type = (downgrade ? "-" : "") + effective.updateType().name();

        var style = switch (effective.updateType()) {
            case major -> Style.EMPTY.fg(Color.RED);
            case minor -> Style.EMPTY.fg(Color.YELLOW);
            case patch -> Style.EMPTY.fg(Color.GREEN);
            case none -> Style.EMPTY;
        };

        return Row.from(checkbox, coords, icon, was, is, latest, type).style(style);
    }
}
