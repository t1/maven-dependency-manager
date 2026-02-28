package com.github.t1.mavendep.tui.panel;

import com.github.t1.mavendep.tui.DashboardModel;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;

/// Renders scrollable git diff output.
public class GitDiffPanel {

    public void render(Frame frame, Rect area, DashboardModel model) {
        var lines = model.diffOutputLines();
        var text = lines.isEmpty() ? "No changes" : String.join("\n", lines);

        var visibleLines = Math.max(1, area.height() - 2);
        var scroll = Math.max(0, lines.size() - visibleLines);

        var paragraph = Paragraph.builder()
                .text(text)
                .scroll(scroll)
                .block(Block.builder().borders(Borders.ALL).title("Git Diff").build())
                .build();

        frame.renderWidget(paragraph, area);
    }
}
