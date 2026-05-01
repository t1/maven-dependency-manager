package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.ArtifactRef;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.Logger.LogLevel;
import static com.github.t1.mavendep.domain.Logger.LogMessage;
import static com.github.t1.mavendep.tui.DashboardModel.Phase.SCANNING;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCIES;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.PLUGINS;

/// Holds all mutable state for the TUI dashboard.
/// Designed for testability: no TamboUI dependencies, pure state management.
public class DashboardModel {

    public enum Phase {SCANNING, READY, APPLYING, BUILDING}

    public enum Tab {
        DEPENDENCIES, PLUGINS, BUILD, DIFF, MESSAGES;
        static final Set<Tab> DEPENDENCY_TABS = EnumSet.of(DEPENDENCIES, PLUGINS);
        static final Set<Tab> ALL_TABS = EnumSet.allOf(Tab.class);
    }

    private Phase phase = SCANNING;
    private Tab activeTab = DEPENDENCIES;

    private List<ProjectReport> reports = List.of();
    private List<Path> rootPomFiles = List.of();
    private final Map<Tab, Integer> cursors = new EnumMap<>(Tab.class);
    private final Set<ArtifactRef> selectedKeys = new HashSet<>();

    private record VersionPick(Version target, UpdateType type) {}

    private final Map<ArtifactRef, VersionPick> customVersions = new HashMap<>();

    private int scanCompleted;
    private int scanTotal;
    private String scanCurrentArtifact = "";

    private final List<LogMessage> logMessages = new ArrayList<>();

    private final List<String> buildOutputLines = new ArrayList<>();
    private Integer buildExitCode;

    private final List<String> diffOutputLines = new ArrayList<>();

    private boolean needsRedraw;

    private boolean showAll;

    private List<MenuBinding> bindings = List.of();
    private List<MenuBinding> pickerBindings = List.of();

    private final VersionPickerModel versionPicker = new VersionPickerModel(this::rawFocusedUpdate);

    void setBindings(List<MenuBinding> bindings) {this.bindings = bindings;}

    void setPickerBindings(List<MenuBinding> pickerBindings) {this.pickerBindings = pickerBindings;}

    void setRootPomFiles(List<Path> rootPomFiles) {this.rootPomFiles = rootPomFiles.stream().map(DashboardModel::resolveToPomFile).toList();}

    public String titleText() {
        var roots = reports.stream()
                .filter(report -> rootPomFiles.contains(resolveToPomFile(report.pom().path())))
                .map(report -> displayName(report.pom()))
                .distinct()
                .toList();
        return roots.isEmpty() ? "Maven Dependency Manager" : "Maven Dependency Manager — " + String.join(", ", roots);
    }

    private static String displayName(Pom pom) {
        return (pom.name() == null || pom.name().isBlank()) ? pom.coordinates().artifactId() : pom.name();
    }

    private static Path resolveToPomFile(Path path) {
        var absolute = path.toAbsolutePath().normalize();
        return Files.isDirectory(absolute) ? absolute.resolve("pom.xml") : absolute;
    }

    /// Returns the formatted menu text showing only bindings available on the active tab.
    public String menuText() {
        var active = isVersionPickerOpen() ? pickerBindings : bindings;
        return active.stream()
                .map(b -> b.displayFor(activeTab))
                .flatMap(Optional::stream)
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

    public void toggleShowAll() {
        var oldUpdates = activeUpdates();
        var oldCursor = cursor();
        showAll = !showAll;
        var newUpdates = activeUpdates();
        if (newUpdates.isEmpty()) {
            setCursor(0);
            return;
        }
        for (int i = oldCursor; i >= 0; i--) {
            var newIndex = newUpdates.indexOf(oldUpdates.get(i));
            if (newIndex >= 0) {
                setCursor(newIndex);
                return;
            }
        }
        setCursor(0);
    }

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
        cursors.clear();
        selectedKeys.clear();
        customVersions.clear();
        preselectUncommittedChanges();
        setNeedsRedraw();
    }

    /// Pre-selects updates where the POM version was already changed from the committed version,
    /// e.g. from a previous TUI session that wasn't committed.
    private void preselectUncommittedChanges() {
        allUpdates()
                .filter(u -> u.committedVersion() != null && u.currentVersion() != null)
                .forEach(u -> {
                    setCustomVersion(u, u.currentVersion());
                    selectedKeys.add(selectionKey(u));
                });
    }

    /// Returns the flat list of dependency updates for the active tab.
    public List<Update> activeUpdates() {
        return activeGroupedUpdates().stream()
                .flatMap(e -> e.getValue().stream())
                .toList();
    }

    /// Returns updates grouped by POM file, with show-all filtering applied.
    /// Each entry maps a POM path to its filtered updates for the active tab.
    /// Only used by the table panel for rendering section headers.
    public List<Map.Entry<Path, List<Update>>> activeGroupedUpdates() {
        return reports.stream()
                .map(r -> Map.entry(
                        r.pom().path(),
                        filterUpdates(switch (activeTab) {
                            case DEPENDENCIES -> r.dependencyUpdates();
                            case PLUGINS -> r.pluginUpdates();
                            case BUILD, DIFF, MESSAGES -> List.of();
                        })))
                .filter(e -> !e.getValue().isEmpty())
                .toList();
    }

    private List<Update> filterUpdates(List<Update> updates) {
        if (showAll) return updates;
        return updates.stream()
                .filter(u -> isChange(u)
                             || worstLogLevelFor(u.artifactRef()).isPresent())
                .toList();
    }

    // --- Cursor ---

    public int cursor() {return cursors.getOrDefault(activeTab, 0);}

    private void setCursor(int value) {cursors.put(activeTab, value);}

    public void cursorUp() {
        var c = cursor();
        if (c > 0) setCursor(c - 1);
    }

    public void cursorDown() {
        var max = activeUpdates().size() - 1;
        var c = cursor();
        if (c < max) setCursor(c + 1);
    }

    public void cursorHome() {setCursor(0);}

    public void cursorEnd() {
        setCursor(Math.max(0, activeUpdates().size() - 1));
    }

    private void clampCursor() {
        var max = Math.max(0, activeUpdates().size() - 1);
        if (cursor() > max) setCursor(max);
    }

    // --- Selection ---

    public boolean isSelected(Update update) {
        if (selectedKeys.contains(selectionKey(update))) return true;
        var prop = update.versionProperty();
        if (prop == null) return false;
        return allUpdates().anyMatch(u -> prop.equals(u.versionProperty())
                                          && selectedKeys.contains(selectionKey(u)));
    }

    public void toggleSelection() {
        var updates = activeUpdates();
        var c = cursor();
        if (c < 0 || c >= updates.size()) return;
        var update = updates.get(c);
        if (!isChange(update)) return;
        if (isSelected(update)) {
            deselect(propertySiblings(update));
            clampCursor();
        } else {
            selectedKeys.add(selectionKey(update));
        }
    }

    private Stream<Update> propertySiblings(Update update) {
        var prop = update.versionProperty();
        if (prop == null) return Stream.of(update);
        return allUpdates().filter(u -> prop.equals(u.versionProperty()));
    }

    private Stream<Update> allUpdates() {
        return reports.stream()
                .flatMap(r -> Stream.concat(
                        r.dependencyUpdates().stream(),
                        r.pluginUpdates().stream()));
    }

    public void toggleSelectAll() {
        if (allSelected()) selectNone();
        else selectAll();
    }

    private Stream<Update> selectableUpdates() {
        return activeUpdates().stream().filter(this::isChange);
    }

    private boolean allSelected() {
        return selectableUpdates().allMatch(this::isSelected);
    }

    public void selectAll() {
        selectableUpdates().forEach(u -> selectedKeys.add(selectionKey(u)));
    }

    public void selectNone() {
        deselect(activeUpdates().stream());
    }

    private void deselect(Stream<Update> updates) {
        updates.forEach(u -> {
            selectedKeys.remove(selectionKey(u));
            customVersions.remove(selectionKey(u));
        });
    }

    public long selectedCount() {
        return activeUpdates().stream().filter(this::isSelected).count();
    }

    /// Returns the selected updates, formatted for [com.github.t1.mavendep.domain.Pom#apply]:
    /// `declaredVersion` = original version in the POM (what to find or override locally),
    /// `latestVersion` = target version (what to replace it with).
    public Stream<Update> selectedUpdates() {
        return allUpdates()
                .filter(this::isChange)
                .filter(this::isSelected)
                .map(this::toPomUpdate);
    }

    private Update toPomUpdate(Update update) {
        var pick = customVersions.get(selectionKey(update));
        if (pick == null) return update;
        return new Update(
                update.dependency(),
                pick.target(),
                update.availableVersions(),
                pick.type());
    }

    private static ArtifactRef selectionKey(Update u) {return u.artifactRef();}

    // --- Custom version (version picker) ---

    public void setCustomVersion(Update original, Version targetVersion) {
        var committed = committedVersion(original);
        if (Objects.equals(targetVersion, committed)) {
            deselect(propertySiblings(original));
            return;
        }
        propertySiblings(original).forEach(sibling -> {
            var siblingCommitted = committedVersion(sibling);
            var downgrade = siblingCommitted != null && targetVersion.compareTo(siblingCommitted) < 0;
            var type = downgrade
                    ? UpdateType.between(targetVersion, siblingCommitted)
                    : UpdateType.between(siblingCommitted, targetVersion);
            customVersions.put(selectionKey(sibling), new VersionPick(targetVersion, type));
        });
    }

    /// Returns the committed version (from git HEAD) for this update,
    /// falling back to the current effective version if there are no uncommitted changes.
    public Version committedVersion(Update update) {
        var committed = update.committedVersion();
        return committed != null ? committed : update.currentVersion();
    }

    /// Returns true if the committed version differs from the target version
    /// (latest, or custom pick if one was set).
    public boolean isChange(Update update) {
        var committed = committedVersion(update);
        if (committed == null) return false;
        var pick = customVersions.get(selectionKey(update));
        if (pick != null) return pick.target().compareTo(committed) != 0;
        return update.latestVersion() != null
               && update.latestVersion().compareTo(committed) != 0;
    }

    public Version declaredVersion(Update update) {
        if (!isSelected(update)) return update.declaredVersion();
        var pick = customVersions.get(selectionKey(update));
        return pick != null ? pick.target() : update.latestVersion();
    }

    /// Returns the version currently effective for this update:
    /// original effective version if not selected, latest or custom pick if selected.
    public Version currentVersion(Update update) {
        if (!isSelected(update)) return update.currentVersion();
        var pick = customVersions.get(selectionKey(update));
        return pick != null ? pick.target() : update.latestVersion();
    }

    public Update effectiveUpdate(Update update) {
        var pick = customVersions.get(selectionKey(update));
        if (pick != null) return new Update(
                update.dependency().with(pick.target()),
                pick.target(),
                update.latestVersion(),
                update.availableVersions(),
                pick.type(),
                update.committedVersion());
        var committed = committedVersion(update);
        var type = UpdateType.between(committed, update.latestVersion());
        if (type == update.updateType()) return update;
        return new Update(
                update.dependency(),
                update.currentVersion(),
                update.latestVersion(),
                update.availableVersions(),
                type,
                update.committedVersion());
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

    public boolean hasLogMessagesFor(ArtifactRef artifact) {
        return logMessages.stream().anyMatch(m -> artifact.equals(m.artifact()));
    }

    public List<LogMessage> logMessagesFor(ArtifactRef artifact) {
        return logMessages.stream().filter(m -> artifact.equals(m.artifact())).toList();
    }

    public Optional<LogLevel> worstLogLevelFor(ArtifactRef artifact) {
        return logMessagesFor(artifact).stream()
                .map(LogMessage::level)
                .filter(l -> l != LogLevel.INFO)
                .max(Enum::compareTo);
    }

    public void clearLogMessages() {logMessages.clear();}

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
            var focusedUpdate = rawFocusedUpdate();
            if (focusedUpdate != null) {
                setCustomVersion(focusedUpdate, picked);
                if (customVersions.containsKey(selectionKey(focusedUpdate))) {
                    selectedKeys.add(selectionKey(focusedUpdate));
                }
            }
        }
    }

    /// Returns the focused [Update] or null if none.
    public Update focusedUpdate() {
        var raw = rawFocusedUpdate();
        return (raw != null) ? effectiveUpdate(raw) : null;
    }

    private Update rawFocusedUpdate() {
        var updates = activeUpdates();
        return updates.isEmpty() ? null : updates.get(cursor());
    }
}
