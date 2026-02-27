package com.github.t1.mavendep.tui.panel;

import com.github.t1.mavendep.tui.DashboardModel;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;

/// Renders scrollable maven build output.
public class BuildOutputPanel {

    public void render(Frame frame, Rect area, DashboardModel model) {
        var lines = model.buildOutputLines();
        var text = String.join("\n", lines);

        var title = "Build Output";
        if (model.buildExitCode() != null) {
            title += (model.buildExitCode() == 0) ? " [SUCCESS]" : " [FAILED: " + model.buildExitCode() + "]";
        }

        var style = model.buildExitCode() != null && model.buildExitCode() != 0
                ? Style.EMPTY.fg(Color.RED)
                : Style.EMPTY;

        // Auto-scroll to bottom
        var visibleLines = Math.max(1, area.height() - 2); // account for borders
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
