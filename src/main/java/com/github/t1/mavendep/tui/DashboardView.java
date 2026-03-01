package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Phase;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.tabs.Tabs;
import dev.tamboui.widgets.tabs.TabsState;

import java.util.stream.Collectors;

import static com.github.t1.mavendep.domain.Logger.LogLevel.INFO;
import static com.github.t1.mavendep.domain.Logger.LogMessage;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.BUILD;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DIFF;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.LOGS;
import static dev.tamboui.style.Overflow.WRAP_WORD;

/// Renders the [DashboardModel] into TamboUI widgets.
public class DashboardView {

    private final DashboardModel model;
    private final ScanProgressPanel scanProgressPanel = new ScanProgressPanel();
    private final DependencyTablePanel dependencyTablePanel = new DependencyTablePanel();
    private final VersionPickerPanel versionPickerPanel = new VersionPickerPanel();
    private final BuildOutputPanel buildOutputPanel = new BuildOutputPanel();
    private final GitDiffPanel gitDiffPanel = new GitDiffPanel();
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
                .titles("Dependencies", "Plugins", "Build Output", "Git Diff", "Logs")
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

        if (model.activeTab() == BUILD) {
            buildOutputPanel.render(frame, area, model);
        } else if (model.activeTab() == DIFF) {
            gitDiffPanel.render(frame, area, model);
        } else if (model.activeTab() == LOGS) {
            renderLogsTab(frame, area);
        } else {
            renderDependencyTabWithErrors(frame, area);
        }

        if (model.isVersionPickerOpen()) {
            versionPickerPanel.render(frame, area, model);
        }
    }

    private void renderDependencyTabWithErrors(Frame frame, Rect area) {
        var focused = model.focusedUpdate();
        var focusedArtifact = focused != null ? focused.artifactRef() : null;
        var messages = model.logMessages().stream()
                .filter(m -> m.level() != INFO)
                .filter(m -> focusedArtifact != null ? focusedArtifact.equals(m.artifact()) : m.artifact() == null)
                .toList();
        if (messages.isEmpty()) {
            dependencyTablePanel.render(frame, area, model);
            return;
        }

        var innerWidth = Math.max(1, area.width() - 2); // account for borders
        var wrappedLineCount = messages.stream()
                .mapToInt(m -> Math.max(1, (m.message().length() + innerWidth - 1) / innerWidth))
                .sum();
        var logHeight = Math.min(wrappedLineCount + 2, area.height() / 3); // +2 for borders, cap at 1/3
        var split = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(logHeight))
                .split(area);

        dependencyTablePanel.render(frame, split.getFirst(), model);

        var text = messages.stream().map(LogMessage::message).collect(Collectors.joining("\n"));
        var visibleLines = Math.max(1, logHeight - 2);
        var scroll = Math.max(0, wrappedLineCount - visibleLines);
        var paragraph = Paragraph.builder()
                .text(text)
                .style(Style.EMPTY.fg(Color.YELLOW))
                .overflow(WRAP_WORD)
                .scroll(scroll)
                .block(Block.builder().borders(Borders.ALL)
                        .title("Errors (" + messages.size() + ")")
                        .build())
                .build();
        frame.renderWidget(paragraph, split.get(1));
    }

    private void renderLogsTab(Frame frame, Rect area) {
        var messages = model.logMessages();
        if (messages.isEmpty()) {
            var block = Block.builder().borders(Borders.ALL).title("Logs").build();
            frame.renderWidget(Paragraph.builder().text("\n  no log messages").block(block).build(), area);
            return;
        }

        var text = messages.stream()
                .map(m -> "[" + m.level() + "] "
                        + (m.artifact() != null ? m.artifact() + ": " : "")
                        + m.message())
                .collect(Collectors.joining("\n"));
        var visibleLines = Math.max(1, area.height() - 2);
        var scroll = Math.max(0, messages.size() - visibleLines);
        var paragraph = Paragraph.builder()
                .text(text)
                .overflow(WRAP_WORD)
                .scroll(scroll)
                .block(Block.builder().borders(Borders.ALL)
                        .title("Logs (" + messages.size() + ")")
                        .build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    private void renderStatusBar(Frame frame, Rect area) {
        if (model.phase() != Phase.READY) {
            var status = switch (model.phase()) {
                case SCANNING -> {
                    var messages = model.logMessages();
                    yield messages.isEmpty() ? "Scanning..." : "Scanning... " + messages.getLast().message();
                }
                case APPLYING -> "Applying updates...";
                case BUILDING -> "Building...";
                case READY -> throw new IllegalStateException();
            };
            frame.renderWidget(Paragraph.builder().text(status).overflow(WRAP_WORD).build(), area);
            return;
        }

        var logCount = model.logMessages().size();
        var logHint = logCount > 0 ? " | " + logCount + " log message" + (logCount > 1 ? "s" : "") : "";
        var left = model.selectedCount() + " selected" + logHint;
        var right = model.menuText();

        var gap = area.width() - left.length() - right.length();
        var status = gap >= 2
                ? left + " ".repeat(gap) + right
                : left + " | " + right;
        frame.renderWidget(Paragraph.builder().text(status).overflow(WRAP_WORD).build(), area);
    }
}
