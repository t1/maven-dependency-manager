package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;

/// Renders the dependency/plugin table with selection checkboxes.
class DependencyTablePanel {

    private static final Style HEADER_STYLE = Style.EMPTY.bold().fg(Color.DARK_GRAY);
    private static final Path CWD = Path.of("").toAbsolutePath();

    private record ScopeGroup(String label, List<Update> updates) {}

    private final TableState tableState = new TableState();

    public void render(Frame frame, Rect area, DashboardModel model) {
        var emptyMessage = model.emptyMessage();
        if (emptyMessage != null) {
            var block = Block.builder().borders(Borders.ALL).title(model.activeTab().name()).build();
            var paragraph = Paragraph.builder().text("\n  " + emptyMessage).block(block).build();
            frame.renderWidget(paragraph, area);
            return;
        }

        var grouped = model.activeGroupedUpdates();
        var multiPom = grouped.size() > 1;
        var rows = buildGroupedRows(grouped, multiPom, model);
        tableState.select(toVisualIndex(model.cursor(), grouped, multiPom));

        var widths = columnWidths(model);
        var table = Table.builder()
                .header(Row.from("      Group:Artifact", "", "Declared ", "Update ")
                        .style(Style.EMPTY.bold()))
                .rows(rows)
                .widths(widths)
                .highlightStyle(Style.EMPTY.bg(Color.DARK_GRAY))
                .highlightSpacing(Table.HighlightSpacing.NEVER)
                .block(Block.builder().borders(Borders.ALL).title(model.activeTab().name()).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private static List<Row> buildGroupedRows(List<Map.Entry<Path, List<Update>>> grouped, boolean multiPom, DashboardModel model) {
        var rows = new ArrayList<Row>();
        var focusedIndex = model.cursor();
        var dataIndex = 0;
        for (var entry : grouped) {
            if (multiPom) rows.add(toPomHeaderRow(entry.getKey()));
            for (var scopeGroup : groupByScope(entry.getValue())) {
                if (scopeGroup.label() != null) rows.add(toScopeHeaderRow(scopeGroup.label(), multiPom));
                for (var update : scopeGroup.updates()) {
                    rows.add(toRow(update, model, dataIndex == focusedIndex));
                    dataIndex++;
                }
            }
        }
        return rows;
    }

    private static Row toPomHeaderRow(Path pomPath) {
        var display = pomPath.isAbsolute() ? CWD.relativize(pomPath) : pomPath;
        return Row.from(display.toString(), "", "", "")
                .style(HEADER_STYLE);
    }

    private static Row toScopeHeaderRow(String scope, boolean inset) {
        return Row.from((inset ? "  " : "") + scope, "", "", "")
                .style(HEADER_STYLE);
    }

    /// Maps a flat data index to a visual row index by adding the count of POM and scope header rows above.
    static int toVisualIndex(int dataIndex, List<Map.Entry<Path, List<Update>>> grouped, boolean multiPom) {
        var visualIndex = 0;
        var remaining = dataIndex;
        for (var entry : grouped) {
            if (multiPom) visualIndex++;
            for (var scopeGroup : groupByScope(entry.getValue())) {
                if (scopeGroup.label() != null) visualIndex++;
                if (remaining < scopeGroup.updates().size()) return visualIndex + remaining;
                remaining -= scopeGroup.updates().size();
                visualIndex += scopeGroup.updates().size();
            }
        }
        return visualIndex;
    }

    private static List<ScopeGroup> groupByScope(List<Update> updates) {
        var groups = new ArrayList<ScopeGroup>();
        for (var update : updates) {
            var label = formatScopeLabel(update);
            if (!groups.isEmpty() && Objects.equals(groups.getLast().label(), label)) {
                groups.getLast().updates().add(update);
            } else {
                var groupUpdates = new ArrayList<Update>();
                groupUpdates.add(update);
                groups.add(new ScopeGroup(label, groupUpdates));
            }
        }
        return groups;
    }

    private static String formatScopeLabel(Update update) {
        if (update.isManagement()) {
            var base = switch (update.declaration()) {
                case dependencyManagement -> "dependencyManagement";
                case pluginManagement -> "pluginManagement";
                case direct -> throw new IllegalStateException();
            };
            return update.profile() != null ? base + "@" + update.profile() : base;
        }
        if (update.type() == plugin) return update.profile();
        var base = switch (update.type()) {
            case dependency -> update.scope().toString();
            case parent -> "parent";
            case plugin -> throw new IllegalStateException();
        };
        return update.profile() != null ? base + "@" + update.profile() : base;
    }

    private static Row toRow(Update update, DashboardModel model, boolean focused) {
        var selector = focused ? "> " : "  ";
        var checkbox = !model.isChange(update) ? "    " : model.isSelected(update) ? "[x] " : "[ ] ";
        var coords = selector + checkbox + update.artifactRef();
        var icon = model.worstLogLevelFor(update.artifactRef())
                .map(level -> switch (level) {
                    case ERROR -> "❌";
                    case WARNING -> "⚠️";
                    case INFO -> "";
                })
                .orElse("");
        var declared = formatDeclared(update, model);
        var displayType = UpdateType.between(model.currentVersion(update), update.latestVersion());
        var style = styleFor(displayType, update.currentVersion() != null);

        return Row.from(coords, icon, declared, formatUpdate(update, model)).style(style);
    }

    private static Constraint[] columnWidths(DashboardModel model) {
        var updates = model.activeUpdates();
        return new Constraint[]{
                Constraint.fill(),
                Constraint.length(maxLength(0, updates, update -> model.worstLogLevelFor(update.artifactRef())
                        .map(level -> switch (level) {
                            case ERROR -> "❌";
                            case WARNING -> "⚠️";
                            case INFO -> "";
                        })
                        .orElse(""))),
                Constraint.length(maxLength("Declared ".length(), updates, update -> formatDeclared(update, model))),
                Constraint.length(maxLength("Update ".length(), updates, update -> formatUpdate(update, model)))
        };
    }

    private static int maxLength(int headerLength, List<Update> updates, Function<Update, String> formatter) {
        return Math.max(headerLength, updates.stream().map(formatter).mapToInt(String::length).max().orElse(0));
    }

    static String formatDeclared(Update update, DashboardModel model) {
        var committed = formatVersion(update.committedVersion(), null);
        var managedFallback = model.hasUpstream(update) ? "<managed ↑>" : "<managed>";
        var declared = formatVersion(model.declaredVersion(update), managedFallback);
        var formatted = (committed == null || committed.equals(declared)) ? declared : committed + " → " + declared;
        return formatted + " ";
    }

    static String formatUpdate(Update update, DashboardModel model) {
        var effective = formatVersion(model.currentVersion(update), "?");
        var latest = formatVersion(update.latestVersion(), "?");
        var formatted = effective.equals(latest) ? effective : effective + " → " + latest;
        return formatted + " ";
    }

    private static Style styleFor(UpdateType updateType, boolean resolved) {
        if (!resolved) return Style.EMPTY;
        return switch (updateType) {
            case major -> Style.EMPTY.fg(Color.RED);
            case minor -> Style.EMPTY.fg(Color.GREEN);
            case patch -> Style.EMPTY.fg(Color.BLUE);
            case none -> Style.EMPTY;
        };
    }

    private static String formatVersion(Version version, String fallback) {
        return version != null ? version.toString() : fallback;
    }
}
