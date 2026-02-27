package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Phase;
import com.github.t1.mavendep.tui.DashboardModel.Tab;
import com.github.t1.mavendep.tui.panel.BuildOutputPanel;
import com.github.t1.mavendep.tui.panel.DependencyTablePanel;
import com.github.t1.mavendep.tui.panel.LogPanel;
import com.github.t1.mavendep.tui.panel.ScanProgressPanel;
import com.github.t1.mavendep.tui.panel.VersionPickerPanel;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.style.Overflow;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.tabs.Tabs;
import dev.tamboui.widgets.tabs.TabsState;

/// Renders the [DashboardModel] into TamboUI widgets.
public class DashboardView {

    private final DashboardModel model;
    private final ScanProgressPanel scanProgressPanel = new ScanProgressPanel();
    private final DependencyTablePanel dependencyTablePanel = new DependencyTablePanel();
    private final VersionPickerPanel versionPickerPanel = new VersionPickerPanel();
    private final BuildOutputPanel buildOutputPanel = new BuildOutputPanel();
    private final LogPanel logPanel = new LogPanel();
    private final TabsState tabsState = new TabsState(0);

    public DashboardView(DashboardModel model) {
        this.model = model;
    }

    public void render(Frame frame) {
        var areas = Layout.vertical()
                .constraints(
                        Constraint.length(3),  // title bar
                        Constraint.length(3),  // tabs
                        Constraint.fill(),     // content
                        Constraint.length(2))  // status bar
                .split(frame.area());

        renderTitleBar(frame, areas.get(0));
        renderTabs(frame, areas.get(1));
        renderContent(frame, areas.get(2));
        renderStatusBar(frame, areas.get(3));
    }

    private void renderTitleBar(Frame frame, Rect area) {
        var title = Paragraph.builder()
                .text("Maven Dependency Manager")
                .style(Style.EMPTY.bold().fg(Color.CYAN))
                .block(Block.builder().borders(Borders.ALL).build())
                .build();
        frame.renderWidget(title, area);
    }

    private void renderTabs(Frame frame, Rect area) {
        tabsState.select(model.activeTab().ordinal());
        var tabs = Tabs.builder()
                .titles("Dependencies", "Plugins", "Build Output", "Log")
                .highlightStyle(Style.EMPTY.bold().fg(Color.YELLOW))
                .divider(" | ")
                .block(Block.builder().borders(Borders.ALL).build())
                .build();
        frame.renderStatefulWidget(tabs, area, tabsState);
    }

    private void renderContent(Frame frame, Rect area) {
        if (model.phase() == Phase.SCANNING) {
            scanProgressPanel.render(frame, area, model);
            return;
        }

        switch (model.activeTab()) {
            case BUILD -> buildOutputPanel.render(frame, area, model);
            case LOG -> logPanel.render(frame, area, model);
            default -> dependencyTablePanel.render(frame, area, model);
        }

        if (model.isVersionPickerOpen()) {
            versionPickerPanel.render(frame, area, model);
        }
    }

    private void renderStatusBar(Frame frame, Rect area) {
        var status = switch (model.phase()) {
            case SCANNING -> {
                var messages = model.logMessages();
                yield messages.isEmpty() ? "Scanning..." : "Scanning... " + messages.getLast();
            }
            case APPLYING -> "Applying updates...";
            case BUILDING -> "Building...";
            case READY -> {
                var logCount = model.logMessages().size();
                var logHint = logCount > 0 ? " | " + logCount + " log message" + (logCount > 1 ? "s" : "") : "";
                yield model.selectedCount() + " selected" + logHint + " | " +
                        "[Space] toggle [Enter] pick version [s]how all [u]pdate [b]uild [r]escan Tab/[ ] tabs [q]uit";
            }
        };

        var paragraph = Paragraph.builder().text(status).overflow(Overflow.WRAP_WORD).build();
        frame.renderWidget(paragraph, area);
    }
}
