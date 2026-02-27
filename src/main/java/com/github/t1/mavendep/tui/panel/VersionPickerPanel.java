package com.github.t1.mavendep.tui.panel;

import com.github.t1.mavendep.tui.DashboardModel;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;

/// Renders an overlay list for choosing a target version.
public class VersionPickerPanel {

    private final ListState listState = new ListState();

    public void render(Frame frame, Rect area, DashboardModel model) {
        var focused = model.focusedUpdate();
        if (focused == null) return;

        var versions = model.currentVersionPickerVersions();
        listState.select(model.versionPickerCursor());

        var items = versions.stream()
                .map(v -> ListItem.from(v.toString()))
                .toList();

        var list = ListWidget.builder()
                .items(items)
                .highlightStyle(Style.EMPTY.bg(Color.BLUE))
                .highlightSymbol("> ")
                .block(Block.builder().borders(Borders.ALL)
                        .title("Pick version for " + focused.groupId() + ":" + focused.artifactId())
                        .build())
                .build();

        // Center the overlay on the screen
        var overlayWidth = Math.min(area.width() - 4, 60);
        var overlayHeight = Math.min(area.height() - 4, versions.size() + 2);
        var x = area.left() + (area.width() - overlayWidth) / 2;
        var y = area.top() + (area.height() - overlayHeight) / 2;
        var overlayArea = new Rect(x, y, overlayWidth, overlayHeight);

        frame.renderStatefulWidget(list, overlayArea, listState);
    }
}
