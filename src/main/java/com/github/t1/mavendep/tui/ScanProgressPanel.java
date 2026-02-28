package com.github.t1.mavendep.tui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.gauge.LineGauge;
import dev.tamboui.widgets.paragraph.Paragraph;

/// Renders a compact progress indicator during dependency scanning.
class ScanProgressPanel {

    public void render(Frame frame, Rect area, DashboardModel model) {
        var total = model.scanTotal();
        var ratio = (total > 0) ? (double) model.scanCompleted() / total : 0.0;
        var label = model.scanCurrentArtifact().isEmpty()
                ? "Scanning..."
                : "Scanning " + model.scanCurrentArtifact() + " (" + model.scanCompleted() + "/" + total + ")";

        var block = Block.builder().borders(Borders.ALL).title("Scanning Dependencies").build();
        var inner = block.inner(area);
        frame.renderWidget(block, area);

        var rows = Layout.vertical()
                .constraints(Constraint.length(1), Constraint.length(1), Constraint.fill())
                .split(inner);

        frame.renderWidget(Paragraph.builder().text(label).build(), rows.getFirst());

        var gauge = LineGauge.builder()
                .ratio(ratio)
                .filledStyle(Style.EMPTY.fg(Color.GREEN))
                .unfilledStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                .lineSet(LineGauge.THICK)
                .build();
        frame.renderWidget(gauge, rows.get(1));
    }
}
