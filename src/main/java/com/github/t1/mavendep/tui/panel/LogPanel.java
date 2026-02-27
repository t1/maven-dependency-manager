package com.github.t1.mavendep.tui.panel;

import com.github.t1.mavendep.tui.DashboardModel;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;

/// Renders scrollable log messages from scanning.
public class LogPanel {

    public void render(Frame frame, Rect area, DashboardModel model) {
        var lines = model.logMessages();
        var text = lines.isEmpty() ? "No log messages." : String.join("\n", lines);

        var title = "Log (" + lines.size() + ")";

        var style = lines.isEmpty() ? Style.EMPTY : Style.EMPTY.fg(Color.YELLOW);

        // Auto-scroll to bottom
        var visibleLines = Math.max(1, area.height() - 2);
        var scroll = Math.max(0, lines.size() - visibleLines);

        var paragraph = Paragraph.builder()
                .text(text)
                .style(style)
                .scroll(scroll)
                .block(Block.builder().borders(Borders.ALL).title(title).build())
                .build();

        frame.renderWidget(paragraph, area);
    }
}
