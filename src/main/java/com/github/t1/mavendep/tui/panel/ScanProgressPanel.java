package com.github.t1.mavendep.tui.panel;

import com.github.t1.mavendep.tui.DashboardModel;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.gauge.Gauge;

/// Renders a progress gauge during dependency scanning.
public class ScanProgressPanel {

    public void render(Frame frame, Rect area, DashboardModel model) {
        var total = model.scanTotal();
        var ratio = (total > 0) ? (double) model.scanCompleted() / total : 0.0;
        var label = model.scanCurrentArtifact().isEmpty()
                ? "Scanning..."
                : "Scanning " + model.scanCurrentArtifact() + " (" + model.scanCompleted() + "/" + total + ")";

        var gauge = Gauge.builder()
                .ratio(ratio)
                .label(label)
                .gaugeStyle(Style.EMPTY.fg(Color.CYAN))
                .block(Block.builder().borders(Borders.ALL).title("Scanning Dependencies").build())
                .build();

        frame.renderWidget(gauge, area);
    }
}
