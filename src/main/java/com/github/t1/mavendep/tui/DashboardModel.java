package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.Logger.LogMessage;
import static com.github.t1.mavendep.tui.DashboardModel.Phase.SCANNING;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCIES;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DIFF;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.PLUGINS;

/// Holds all mutable state for the TUI dashboard.
/// Designed for testability: no TamboUI dependencies, pure state management.
public class DashboardModel {

    public enum Phase {SCANNING, READY, APPLYING, BUILDING}

    public enum Tab {DEPENDENCIES, PLUGINS, BUILD, DIFF}

    private Phase phase = SCANNING;
    private Tab activeTab = DEPENDENCIES;

    private List<ProjectReport> reports = List.of();
    private int cursor;
    private final Set<String> selectedKeys = new HashSet<>();
    private final Map<String, DependencyUpdate> customVersions = new HashMap<>();

    private int scanCompleted;
    private int scanTotal;
    private String scanCurrentArtifact = "";

    private final List<LogMessage> logMessages = new ArrayList<>();

    private final List<String> buildOutputLines = new ArrayList<>();
    private Integer buildExitCode;

    private final List<String> diffOutputLines = new ArrayList<>();

    private boolean needsRedraw;

    private boolean showAll;

    private static final Set<Tab> DEPENDENCY_TABS = EnumSet.of(DEPENDENCIES, PLUGINS);
    private static final Set<Tab> ALL_TABS = EnumSet.allOf(Tab.class);

    private static final List<MenuBinding> STATIC_BINDINGS = List.of(
            new MenuBinding("[Space] toggle", DEPENDENCY_TABS),
            new MenuBinding("[Enter] pick version", DEPENDENCY_TABS),
            new MenuBinding("[s]how all", DEPENDENCY_TABS),
            new MenuBinding("[b]uild", ALL_TABS),
            new MenuBinding("[r]escan", ALL_TABS),
            new MenuBinding("Tab/[ ] tabs", ALL_TABS),
            new MenuBinding("[q]uit", ALL_TABS));

    private final VersionPickerModel versionPicker = new VersionPickerModel(this::rawFocusedUpdate);

    // --- Menu ---

    /// Returns the formatted menu text showing only bindings available on the active tab.
    public String menuText() {
        var dBinding = (activeTab == DIFF)
                ? new MenuBinding("[d]ependencies", ALL_TABS)
                : new MenuBinding("[d]iff", ALL_TABS);

        return Stream.concat(STATIC_BINDINGS.stream(), Stream.of(dBinding))
                .filter(b -> b.isAvailableOn(activeTab))
                .map(MenuBinding::display)
                .collect(Collectors.joining(" "));
    }

    /// Returns a message to display when the dependency/plugin list is empty, or null if there are items.
    public String emptyMessage() {
        var updates = activeUpdates();
        if (!updates.isEmpty()) return null;
        if (showAll) return activeTab == PLUGINS ? "no plugins" : "no dependencies";
        return "no updates available";
    }

    // --- needs redraw flag ---

    /// Returns true if the model has been modified since the last [#clearNeedsRedraw] call.
    /// Used by the controller to trigger redraws for background state changes
    /// that arrive via [dev.tamboui.tui.TuiRunner#runOnRenderThread],
    /// which executes the update but does not trigger a redraw on its own.
    public boolean needsRedraw() {return needsRedraw;}

    private void setNeedsRedraw() {needsRedraw = true;}

    public void clearNeedsRedraw() {needsRedraw = false;}

    // --- Show all ---

    public boolean showAll() {return showAll;}

    public void setShowAll(boolean showAll) {this.showAll = showAll;}

    public void toggleShowAll() {showAll = !showAll;}

    // --- Phase ---

    public Phase phase() {return phase;}

    public void setPhase(Phase phase) {
        this.phase = phase;
        setNeedsRedraw();
    }

    // --- Tab ---

    public Tab activeTab() {return activeTab;}

    public void setActiveTab(Tab tab) {this.activeTab = tab;}

    public void nextTab() {
        var values = Tab.values();
        activeTab = values[(activeTab.ordinal() + 1) % values.length];
    }

    public void previousTab() {
        var values = Tab.values();
        activeTab = values[(activeTab.ordinal() - 1 + values.length) % values.length];
    }

    // --- Reports ---

    public List<ProjectReport> reports() {return reports;}

    public void setReports(List<ProjectReport> reports) {
        this.reports = reports;
        cursor = 0;
        selectedKeys.clear();
        customVersions.clear();
        setNeedsRedraw();
    }

    /// Returns the flat list of dependency updates for the active tab.
    public List<DependencyUpdate> activeUpdates() {
        var stream = reports.stream()
                .flatMap(r -> switch (activeTab) {
                    case DEPENDENCIES -> r.dependencyUpdates().stream();
                    case PLUGINS -> r.pluginUpdates().stream();
                    case BUILD, DIFF -> Stream.of();
                });
        if (!showAll) stream = stream.filter(u -> u.updateType() != UpdateType.none);
        return stream.toList();
    }

    // --- Cursor ---

    public int cursor() {return cursor;}

    public void cursorUp() {
        if (cursor > 0) cursor--;
    }

    public void cursorDown() {
        var max = activeUpdates().size() - 1;
        if (cursor < max) cursor++;
    }

    public void cursorHome() {cursor = 0;}

    public void cursorEnd() {
        cursor = Math.max(0, activeUpdates().size() - 1);
    }

    // --- Selection ---

    public boolean isSelected(DependencyUpdate update) {
        return selectedKeys.contains(selectionKey(update));
    }

    public void toggleSelection() {
        var updates = activeUpdates();
        if (cursor < 0 || cursor >= updates.size()) return;
        var update = updates.get(cursor);
        if (!update.isUpdatable()) return;
        var k = selectionKey(update);
        if (!selectedKeys.remove(k)) selectedKeys.add(k);
    }

    public void toggleSelectAll() {
        if (allSelected()) selectNone();
        else selectAll();
    }

    private Stream<DependencyUpdate> selectableUpdates() {
        return activeUpdates().stream().filter(DependencyUpdate::isUpdatable);
    }

    private boolean allSelected() {
        return selectableUpdates().allMatch(this::isSelected);
    }

    public void selectAll() {
        selectableUpdates().forEach(u -> selectedKeys.add(selectionKey(u)));
    }

    public void selectNone() {
        activeUpdates().forEach(u -> selectedKeys.remove(selectionKey(u)));
    }

    public long selectedCount() {
        return activeUpdates().stream().filter(this::isSelected).count();
    }

    /// Returns the selected updates, applying any custom target versions.
    public Stream<DependencyUpdate> selectedUpdates() {
        return reports.stream()
                .flatMap(r -> Stream.concat(
                        r.dependencyUpdates().stream(),
                        r.pluginUpdates().stream()))
                .filter(DependencyUpdate::isUpdatable)
                .filter(this::isSelected)
                .map(this::applyCustomVersion);
    }

    private DependencyUpdate applyCustomVersion(DependencyUpdate update) {
        var custom = customVersions.get(selectionKey(update));
        return (custom != null) ? custom : update;
    }

    private static String selectionKey(DependencyUpdate u) {return u.groupId() + ":" + u.artifactId();}

    // --- Custom version (version picker) ---

    public void setCustomVersion(DependencyUpdate original, Version targetVersion) {
        var newUpdateType = UpdateType.between(original.currentVersion(), targetVersion);
        var custom = new DependencyUpdate(
                original.dependency(),
                targetVersion,
                original.availableVersions(),
                newUpdateType);
        customVersions.put(selectionKey(original), custom);
    }

    public DependencyUpdate effectiveUpdate(DependencyUpdate update) {
        return applyCustomVersion(update);
    }

    // --- Scan progress ---

    public int scanCompleted() {return scanCompleted;}

    public int scanTotal() {return scanTotal;}

    public String scanCurrentArtifact() {return scanCurrentArtifact;}

    public void updateScanProgress(int completed, int total, String artifactName) {
        this.scanCompleted = completed;
        this.scanTotal = total;
        this.scanCurrentArtifact = artifactName;
        setNeedsRedraw();
    }

    // --- Log messages ---

    public List<LogMessage> logMessages() {return logMessages;}

    public boolean hasLogMessagesFor(String artifact) {
        return logMessages.stream().anyMatch(m -> artifact.equals(m.artifact()));
    }

    public List<LogMessage> logMessagesFor(String artifact) {
        return logMessages.stream().filter(m -> artifact.equals(m.artifact())).toList();
    }

    public void addLogMessage(LogMessage message) {
        logMessages.add(message);
        setNeedsRedraw();
    }

    // --- Build output ---

    public List<String> buildOutputLines() {return buildOutputLines;}

    public Integer buildExitCode() {return buildExitCode;}

    public void addBuildOutputLine(String line) {
        buildOutputLines.add(line);
        setNeedsRedraw();
    }

    public void setBuildExitCode(int exitCode) {
        this.buildExitCode = exitCode;
        setNeedsRedraw();
    }

    public void clearBuildOutput() {
        buildOutputLines.clear();
        buildExitCode = null;
    }

    // --- Diff output ---

    public List<String> diffOutputLines() {return diffOutputLines;}

    public void setDiffOutput(List<String> lines) {
        diffOutputLines.clear();
        diffOutputLines.addAll(lines);
        setNeedsRedraw();
    }

    // --- Version picker ---

    public boolean isVersionPickerOpen() {return versionPicker.isOpen();}

    public void openVersionPicker() {versionPicker.open();}

    public void closeVersionPicker() {versionPicker.close();}

    public int versionPickerCursor() {return versionPicker.cursor();}

    public void versionPickerUp() {versionPicker.cursorUp();}

    public void versionPickerDown() {versionPicker.cursorDown();}

    public List<Version> currentVersionPickerVersions() {return versionPicker.availableVersions();}

    public void confirmVersionPick() {
        var picked = versionPicker.confirmAndClose();
        if (picked != null) {
            setCustomVersion(rawFocusedUpdate(), picked);
        }
    }

    /// Returns the focused [DependencyUpdate] or null if none.
    public DependencyUpdate focusedUpdate() {
        var raw = rawFocusedUpdate();
        return (raw != null) ? effectiveUpdate(raw) : null;
    }

    private DependencyUpdate rawFocusedUpdate() {
        var updates = activeUpdates();
        return updates.isEmpty() ? null : updates.get(cursor);
    }
}
