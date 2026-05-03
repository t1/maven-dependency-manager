package com.github.t1.mavendep.tui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.paragraph.Paragraph;

import static dev.tamboui.style.Color.CYAN;
import static dev.tamboui.style.Color.DARK_GRAY;
import static dev.tamboui.style.Color.WHITE;
import static dev.tamboui.style.Style.EMPTY;
import static dev.tamboui.widgets.Clear.clear;
import static dev.tamboui.widgets.block.Borders.ALL;

/// Renders an overlay list for choosing a target version.
class VersionPickerPanel {

    private static final int HEADER_LINES = 3;
    private static final int SEPARATOR_LINE = 1;

    private final ListState listState = new ListState();

    public void render(Frame frame, Rect area, DashboardModel model) {
        var focused = model.focusedUpdate();
        if (focused == null) return;

        var entries = model.currentVersionPickerEntries();
        listState.select(model.versionPickerCursor());

        var items = entries.stream()
                .map(entry -> ListItem.from(entry.label()))
                .toArray(ListItem[]::new);

        var list = ListWidget.builder()
                .items(items)
                .highlightStyle(EMPTY.bold().fg(WHITE).bg(DARK_GRAY))
                .highlightSymbol("> ")
                .build();

        var header = Paragraph.builder()
                .text(" Pick version for\n " + focused.groupId() + "\n " + focused.artifactId())
                .style(EMPTY.bold().fg(CYAN))
                .build();

        // Center the overlay on the screen; +2 for borders
        var overlayWidth = Math.min(area.width() - 4, 40);
        var overlayHeight = Math.min(area.height() - 4, entries.size() + HEADER_LINES + SEPARATOR_LINE + 2);
        var x = area.left() + (area.width() - overlayWidth) / 2;
        var y = area.top() + (area.height() - overlayHeight) / 2;
        var overlayArea = new Rect(x, y, overlayWidth, overlayHeight);

        frame.renderWidget(clear(), overlayArea);
        var block = Block.builder().borders(ALL).build();
        var inner = block.inner(overlayArea);
        frame.renderWidget(block, overlayArea);

        var rows = Layout.vertical()
                .constraints(Constraint.length(HEADER_LINES), Constraint.length(SEPARATOR_LINE), Constraint.fill())
                .split(inner);
        frame.renderWidget(header, rows.getFirst());

        var separator = "├" + "─".repeat(Math.max(0, overlayWidth - 2)) + "┤";
        var separatorArea = new Rect(overlayArea.left(), rows.get(1).top(), overlayWidth, 1);
        frame.renderWidget(Paragraph.builder().text(separator).build(), separatorArea);

        frame.renderStatefulWidget(list, rows.get(2), listState);
    }
}
