package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
import com.github.t1.mavendep.domain.VersionStatus;
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

    static record VisualRow(Row row, boolean header) {}

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
        var visualRows = buildVisualRows(grouped, multiPom, model);
        tableState.select(toVisualIndex(model.cursor(), grouped, multiPom));
        revealHeadersAboveSelection(tableState, visualRows);

        var widths = columnWidths(model);
        var table = Table.builder()
                .header(Row.from("      Group:Artifact", "", "Declared ", "Update ")
                        .style(Style.EMPTY.bold()))
                .rows(visualRows.stream().map(VisualRow::row).toList())
                .widths(widths)
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bg(Color.DARK_GRAY))
                .highlightSpacing(Table.HighlightSpacing.NEVER)
                .block(Block.builder().borders(Borders.ALL).title(model.activeTab().name()).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    static void revealHeadersAboveSelection(TableState tableState, List<VisualRow> rows) {
        var selected = tableState.selected();
        if (selected == null || selected <= 0 || selected >= rows.size() || rows.get(selected).header()) return;

        var headerStart = selected;
        while (headerStart > 0 && rows.get(headerStart - 1).header()) headerStart--;
        if (headerStart == selected) return;

        var headerOffset = 0;
        for (var i = 0; i < headerStart; i++) {
            headerOffset += rows.get(i).row().totalHeight();
        }
        if (tableState.offset() > headerOffset) tableState.setOffset(headerOffset);
    }

    private static List<VisualRow> buildVisualRows(List<Map.Entry<Path, List<Update>>> grouped, boolean multiPom, DashboardModel model) {
        var rows = new ArrayList<VisualRow>();
        var focusedIndex = model.cursor();
        var dataIndex = 0;
        for (var entry : grouped) {
            if (multiPom) rows.add(new VisualRow(toPomHeaderRow(entry.getKey()), true));
            for (var scopeGroup : groupByScope(entry.getValue())) {
                if (scopeGroup.label() != null) rows.add(new VisualRow(toScopeHeaderRow(scopeGroup.label(), multiPom), true));
                for (var update : scopeGroup.updates()) {
                    rows.add(new VisualRow(toRow(update, model, dataIndex == focusedIndex), false));
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
        var checkbox = formatCheckbox(update, model);
        var coords = selector + checkbox + update.artifactRef();
        var icon = model.worstLogLevelFor(update.artifactRef())
                .map(level -> switch (level) {
                    case ERROR -> "❌";
                    case WARNING -> "⚠️";
                    case INFO -> "";
                })
                .orElse("");
        var declared = formatDeclared(update, model);
        var style = styleFor(update, model);

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

    static String formatCheckbox(Update update, DashboardModel model) {
        if (!model.showsCheckbox(update)) return "    ";
        return model.isSelected(update) ? "[x] " : "[ ] ";
    }

    static String formatDeclared(Update update, DashboardModel model) {
        var committed = formatVersion(update.committedVersion(), null);
        var managedFallback = model.hasUpstream(update) ? "<managed ↑>" : "<managed>";
        var declared = formatVersion(model.declaredVersion(update), managedFallback);
        var formatted = (committed == null || committed.equals(declared)) ? declared : committed + " → " + declared;
        return formatted + " ";
    }

    static String formatUpdate(Update update, DashboardModel model) {
        var declaredVersion = model.declaredVersion(update);
        var effectiveVersion = model.currentVersion(update);
        var effective = formatVersion(effectiveVersion, "?");
        var latest = formatVersion(update.latestVersion(), "?");
        var formatted = switch (VersionStatus.of(effectiveVersion, update.latestVersion())) {
            case aheadOfLatestRelease -> effective + " > " + latest;
            case upToDate -> effective;
            case upgradeAvailable -> shouldShowOnlyTarget(declaredVersion, effectiveVersion) ? latest
                    : effective.equals(latest) ? effective : effective + " → " + latest;
            case unknownCurrentVersion, noReleasedVersionAvailable -> effective.equals(latest) ? effective : effective + " → " + latest;
        };
        return formatted + " ";
    }

    private static boolean shouldShowOnlyTarget(Version declaredVersion, Version effectiveVersion) {
        return declaredVersion != null && declaredVersion.equals(effectiveVersion);
    }

    static Style styleFor(Update update, DashboardModel model) {
        if (!model.isSuggested(update) || update.currentVersion() == null) return Style.EMPTY;

        var currentVersion = model.currentVersion(update);
        var versionStatus = VersionStatus.of(currentVersion, update.latestVersion());
        var updateType = versionStatus.isUpdateAvailable()
                ? UpdateType.between(currentVersion, update.latestVersion())
                : UpdateType.none;
        return switch (versionStatus) {
            case aheadOfLatestRelease -> Style.EMPTY.fg(Color.RED);
            case upgradeAvailable -> switch (updateType) {
                case major -> Style.EMPTY.fg(Color.RED);
                case minor -> Style.EMPTY.fg(Color.GREEN);
                case patch -> Style.EMPTY.fg(Color.BLUE);
                case none -> Style.EMPTY;
            };
            case unknownCurrentVersion, noReleasedVersionAvailable, upToDate -> Style.EMPTY;
        };
    }

    private static String formatVersion(Version version, String fallback) {
        return version != null ? version.toString() : fallback;
    }
}
