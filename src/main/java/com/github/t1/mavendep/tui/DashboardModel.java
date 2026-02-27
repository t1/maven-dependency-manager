package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/// Holds all mutable state for the TUI dashboard.
/// Designed for testability: no TamboUI dependencies, pure state management.
public class DashboardModel {

    public enum Phase {SCANNING, READY, APPLYING, BUILDING}

    public enum Tab {DEPENDENCIES, PLUGINS, BUILD}

    private Phase phase = Phase.SCANNING;
    private Tab activeTab = Tab.DEPENDENCIES;

    private List<ProjectReport> reports = List.of();
    private int cursor;
    private final Set<String> selectedKeys = new HashSet<>();
    private final Map<String, DependencyUpdate> customVersions = new HashMap<>();

    private int scanCompleted;
    private int scanTotal;
    private String scanCurrentArtifact = "";

    private final List<String> buildOutputLines = new ArrayList<>();
    private Integer buildExitCode;

    private boolean dirty;

    private boolean versionPickerOpen;
    private int versionPickerCursor;

    // --- Dirty flag ---

    /// Returns true if the model has been modified since the last [#clearDirty] call.
    /// Used by the controller to trigger redraws for background state changes
    /// that arrive via [dev.tamboui.tui.TuiRunner#runOnRenderThread],
    /// which executes the update but does not trigger a redraw on its own.
    public boolean isDirty() {return dirty;}

    public void clearDirty() {dirty = false;}

    // --- Phase ---

    public Phase phase() {return phase;}

    public void setPhase(Phase phase) {this.phase = phase; dirty = true;}

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
        dirty = true;
    }

    /// Returns the flat list of dependency updates for the active tab.
    public List<DependencyUpdate> activeUpdates() {
        return reports.stream()
                .flatMap(r -> switch (activeTab) {
                    case DEPENDENCIES -> r.dependencyUpdates().stream();
                    case PLUGINS -> r.pluginUpdates().stream();
                    case BUILD -> Stream.of();
                })
                .filter(DependencyUpdate::isUpdate)
                .toList();
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
        return selectedKeys.contains(key(update));
    }

    public void toggleSelection() {
        var updates = activeUpdates();
        if (cursor < 0 || cursor >= updates.size()) return;
        var k = key(updates.get(cursor));
        if (!selectedKeys.remove(k)) selectedKeys.add(k);
    }

    public void selectAll() {
        activeUpdates().forEach(u -> selectedKeys.add(key(u)));
    }

    public void selectNone() {
        activeUpdates().forEach(u -> selectedKeys.remove(key(u)));
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
                .filter(DependencyUpdate::isUpdate)
                .filter(this::isSelected)
                .map(this::applyCustomVersion);
    }

    private DependencyUpdate applyCustomVersion(DependencyUpdate update) {
        var custom = customVersions.get(key(update));
        return (custom != null) ? custom : update;
    }

    private static String key(DependencyUpdate u) {
        return u.groupId() + ":" + u.artifactId();
    }

    // --- Custom version (version picker) ---

    public void setCustomVersion(DependencyUpdate original, Version targetVersion) {
        var newUpdateType = UpdateType.between(original.currentVersion(), targetVersion);
        var custom = new DependencyUpdate(
                original.dependency(),
                targetVersion,
                original.availableVersions(),
                newUpdateType);
        customVersions.put(key(original), custom);
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
        dirty = true;
    }

    // --- Build output ---

    public List<String> buildOutputLines() {return buildOutputLines;}

    public Integer buildExitCode() {return buildExitCode;}

    public void addBuildOutputLine(String line) {
        buildOutputLines.add(line);
        dirty = true;
    }

    public void setBuildExitCode(int exitCode) {
        this.buildExitCode = exitCode;
        dirty = true;
    }

    public void clearBuildOutput() {
        buildOutputLines.clear();
        buildExitCode = null;
    }

    // --- Version picker ---

    public boolean isVersionPickerOpen() {return versionPickerOpen;}

    public void openVersionPicker() {
        var updates = activeUpdates();
        if (cursor >= 0 && cursor < updates.size()) {
            versionPickerOpen = true;
            versionPickerCursor = 0;
        }
    }

    public void closeVersionPicker() {
        versionPickerOpen = false;
    }

    public int versionPickerCursor() {return versionPickerCursor;}

    public void versionPickerUp() {
        if (versionPickerCursor > 0) versionPickerCursor--;
    }

    public void versionPickerDown() {
        var versions = currentVersionPickerVersions();
        if (versionPickerCursor < versions.size() - 1) versionPickerCursor++;
    }

    public List<Version> currentVersionPickerVersions() {
        var updates = activeUpdates();
        if (cursor < 0 || cursor >= updates.size()) return List.of();
        return updates.get(cursor).availableVersions().reversed().stream()
                .filter(v -> v.isReleased("version picker"))
                .toList();
    }

    public void confirmVersionPick() {
        var updates = activeUpdates();
        if (cursor < 0 || cursor >= updates.size()) return;
        var versions = currentVersionPickerVersions();
        if (versionPickerCursor < 0 || versionPickerCursor >= versions.size()) return;
        var original = updates.get(cursor);
        var picked = versions.get(versionPickerCursor);
        setCustomVersion(original, picked);
        versionPickerOpen = false;
    }

    /// Returns the focused [DependencyUpdate] or null if none.
    public DependencyUpdate focusedUpdate() {
        var updates = activeUpdates();
        if (cursor < 0 || cursor >= updates.size()) return null;
        return effectiveUpdate(updates.get(cursor));
    }
}
