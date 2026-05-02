package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.ArtifactRef;
import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
import com.github.t1.mavendep.tui.DashboardModel.Phase;
import com.github.t1.mavendep.tui.DashboardModel.Tab;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.t1.mavendep.domain.Dependency.Declaration.dependencyManagement;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Logger.LogLevel.ERROR;
import static com.github.t1.mavendep.domain.Logger.LogLevel.INFO;
import static com.github.t1.mavendep.domain.Logger.LogLevel.WARNING;
import static com.github.t1.mavendep.domain.Logger.LogMessage;
import static com.github.t1.mavendep.domain.Scope.DEFAULT;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DashboardModelTest {

    private final DashboardModel model = new DashboardModel();

    @Test void shouldStartInScanningPhase() {
        then(model.phase()).isEqualTo(Phase.SCANNING);
    }

    @Test void shouldStartOnDependenciesTab() {
        then(model.activeTab()).isEqualTo(Tab.DEPENDENCIES);
    }

    @Test void shouldCycleTabsForward() {
        model.nextTab();
        then(model.activeTab()).isEqualTo(Tab.PLUGINS);

        model.nextTab();
        then(model.activeTab()).isEqualTo(Tab.BUILD);

        model.nextTab();
        then(model.activeTab()).isEqualTo(Tab.DIFF);

        model.nextTab();
        then(model.activeTab()).isEqualTo(Tab.MESSAGES);

        model.nextTab();
        then(model.activeTab()).isEqualTo(Tab.DEPENDENCIES);
    }

    @Test void shouldMoveCursorDown() {
        setReportsWithTwoUpdates();

        model.cursorDown();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldNotMoveCursorBelowLast() {
        setReportsWithTwoUpdates();

        model.cursorDown();
        model.cursorDown();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldMoveCursorUp() {
        setReportsWithTwoUpdates();
        model.cursorDown();

        model.cursorUp();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldNotMoveCursorAboveZero() {
        model.cursorUp();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldKeepSeparateCursorPerTab() {
        setReportsWithDependenciesAndPlugins();
        model.cursorDown(); // dependencies cursor = 1

        model.setActiveTab(Tab.PLUGINS);
        then(model.cursor()).isEqualTo(0); // plugins cursor starts at 0

        model.setActiveTab(Tab.DEPENDENCIES);
        then(model.cursor()).isEqualTo(1); // dependencies cursor preserved
    }

    @Test void shouldToggleSelection() {
        setReportsWithTwoUpdates();
        var updates = model.activeUpdates();

        model.toggleSelection();
        then(model.isSelected(updates.getFirst())).isTrue();

        model.toggleSelection();
        then(model.isSelected(updates.getFirst())).isFalse();
    }

    @Test void shouldSelectAll() {
        setReportsWithTwoUpdates();

        model.toggleSelectAll();
        then(model.selectedCount()).isEqualTo(2);
    }

    @Test void shouldDeselectAllWhenAllSelected() {
        setReportsWithTwoUpdates();
        model.selectAll();

        model.toggleSelectAll();
        then(model.selectedCount()).isEqualTo(0);
    }

    @Test void shouldSelectNone() {
        setReportsWithTwoUpdates();
        model.selectAll();

        model.selectNone();
        then(model.selectedCount()).isEqualTo(0);
    }

    @Test void shouldUpdateScanProgress() {
        model.updateScanProgress(3, 10, "org.example:lib");

        then(model.scanCompleted()).isEqualTo(3);
        then(model.scanTotal()).isEqualTo(10);
        then(model.scanCurrentArtifact()).isEqualTo("org.example:lib");
    }

    @Test void shouldAccumulateBuildOutput() {
        model.addBuildOutputLine("line 1");
        model.addBuildOutputLine("line 2");

        then(model.buildOutputLines()).containsExactly("line 1", "line 2");
    }

    @Test void shouldClearBuildOutput() {
        model.addBuildOutputLine("line 1");
        model.setBuildExitCode(0);

        model.clearBuildOutput();

        then(model.buildOutputLines()).isEmpty();
        then(model.buildExitCode()).isNull();
    }

    @Test void shouldOpenAndCloseVersionPicker() {
        setReportsWithTwoUpdates();

        model.openVersionPicker();
        then(model.isVersionPickerOpen()).isTrue();

        model.closeVersionPicker();
        then(model.isVersionPickerOpen()).isFalse();
    }

    @Test void shouldSetCustomVersionAsCurrent() {
        setReportsWithTwoUpdates();
        var updates = model.activeUpdates();
        var original = updates.getFirst();
        var customVersion = Version.fromString("5.9.0");

        model.setCustomVersion(original, customVersion);

        var effective = model.effectiveUpdate(original);
        then(effective.currentVersion()).isEqualTo(customVersion);
        then(effective.latestVersion()).isEqualTo(original.latestVersion());
    }

    @Test void shouldComputeDowngradeTypeFromCommittedVersion() {
        setReportsWithTwoUpdates();
        var original = model.activeUpdates().getFirst(); // 5.10.0 → 6.0.3

        model.setCustomVersion(original, Version.fromString("5.9.0"));

        var effective = model.effectiveUpdate(original);
        then(effective.updateType()).isEqualTo(UpdateType.minor); // 5.10.0 → 5.9.0
    }

    @Test void shouldReturnPomCompatibleUpdateForCustomVersion() {
        setReportsWithTwoUpdates();
        var original = model.activeUpdates().getFirst(); // 5.10.0 → 6.0.3
        var picked = Version.fromString("5.9.0");
        model.setCustomVersion(original, picked);
        model.selectAll();

        var selected = model.selectedUpdates().toList();

        var update = selected.stream()
                .filter(u -> u.artifactId().equals("junit-jupiter"))
                .findFirst().orElseThrow();
        then(update.currentVersion()).isEqualTo(Version.fromString("5.10.0")); // original, for Pom.Updater to find
        then(update.latestVersion()).isEqualTo(picked); // target, for Pom.Updater to replace with
    }

    @Test void shouldRevertToOriginalVersionOnDeselect() {
        setReportsWithTwoUpdates();
        var original = model.activeUpdates().getFirst();
        model.setCustomVersion(original, Version.fromString("5.9.0"));
        model.toggleSelection(); // select

        model.toggleSelection(); // deselect

        then(model.effectiveUpdate(original)).isEqualTo(original);
    }

    @Test void shouldShowOriginalVersionAsCurrentWhenNotSelected() {
        setReportsWithTwoUpdates();
        var update = model.activeUpdates().getFirst();

        then(model.currentVersion(update)).isEqualTo(update.currentVersion());
    }

    @Test void shouldShowLatestVersionAsCurrentWhenSelected() {
        setReportsWithTwoUpdates();
        var update = model.activeUpdates().getFirst();
        model.toggleSelection();

        then(model.currentVersion(update)).isEqualTo(update.latestVersion());
    }

    @Test void shouldShowPickedVersionAsCurrentWhenCustomSelected() {
        setReportsWithTwoUpdates();
        var update = model.activeUpdates().getFirst();
        var picked = Version.fromString("5.9.0");
        model.setCustomVersion(update, picked);
        model.toggleSelection();

        then(model.currentVersion(update)).isEqualTo(picked);
    }

    @Test void shouldShowCurrentVersionAsCommittedWhenNoGitChange() {
        setReportsWithTwoUpdates();
        var update = model.activeUpdates().getFirst();

        then(model.committedVersion(update)).isEqualTo(update.currentVersion());
    }

    @Test void shouldShowGitCommittedVersion() {
        setReportsWithCommittedVersion();
        var update = model.activeUpdates().getFirst();

        then(model.committedVersion(update)).isEqualTo(Version.fromString("5.9.0"));
    }

    @Test void shouldComputeUpdateTypeFromCommittedVersion() {
        setReportsWithCommittedVersion(); // committed 5.9.0, current 5.10.0, latest 6.0.3
        var update = model.activeUpdates().getFirst();

        var effective = model.effectiveUpdate(update);

        // type is minor (5.9.0 → 5.10.0) because the POM change is pre-selected as custom pick
        then(effective.updateType()).isEqualTo(UpdateType.minor);
    }

    @Test void shouldDetectChangeFromCommittedVersion() {
        setReportsWithCommittedVersion(); // committed 5.9.0 ≠ latest 6.0.3
        var update = model.activeUpdates().getFirst();

        then(model.isChange(update)).isTrue();
    }

    @Test void shouldPreselectUncommittedPomChanges() {
        setReportsWithCommittedVersion(); // committed 5.9.0, current 5.10.0, latest 6.0.3
        var update = model.activeUpdates().getFirst();

        then(model.isSelected(update)).isTrue();
        then(model.currentVersion(update)).isEqualTo(Version.fromString("5.10.0"));
    }

    @Test void shouldRevertCurrentToOriginalOnDeselect() {
        setReportsWithTwoUpdates();
        var update = model.activeUpdates().getFirst();
        model.toggleSelection(); // select
        model.toggleSelection(); // deselect

        then(model.currentVersion(update)).isEqualTo(update.currentVersion());
    }

    @Test void shouldDeselectWhenPickingCommittedVersion() {
        setReportsWithTwoUpdates();
        var original = model.activeUpdates().getFirst();
        model.toggleSelection(); // select first

        model.setCustomVersion(original, original.currentVersion());

        then(model.isSelected(original)).isFalse();
        then(model.effectiveUpdate(original)).isEqualTo(original);
    }

    @Test void shouldAutoSelectOnVersionPick() {
        setReportsWithTwoUpdates();
        model.openVersionPicker();

        model.confirmVersionPick();

        then(model.isSelected(model.activeUpdates().getFirst())).isTrue();
    }

    @Test void shouldTreatCustomPickOnUpToDateDepAsChange() {
        setReportsWithMixedUpdates();
        model.setShowAll(true);
        model.cursorDown(); // focus on up-to-date dep
        var upToDate = model.activeUpdates().get(1);
        var picked = Version.fromString("0.9.0");

        model.setCustomVersion(upToDate, picked);

        then(model.isChange(upToDate)).isTrue();
    }

    @Test void shouldClampCursorWhenDeselectingCustomPickedDepWithShowAllOff() {
        setReportsWithMixedUpdates();
        model.setShowAll(true);
        model.cursorDown(); // focus on up-to-date dep (index 1)
        var upToDate = model.activeUpdates().get(1);
        model.setCustomVersion(upToDate, Version.fromString("0.9.0"));
        model.toggleSelection(); // select it
        model.toggleShowAll(); // show-all OFF — dep stays visible (isChange true)
        then(model.activeUpdates()).hasSize(2); // both visible

        model.cursorDown(); // cursor at 1 (the custom-picked dep)
        model.toggleSelection(); // deselect — removes custom version

        then(model.activeUpdates()).hasSize(1); // up-to-date dep filtered out
        then(model.cursor()).isLessThan(model.activeUpdates().size());
        then(model.focusedUpdate()).isNotNull(); // should not crash
    }

    @Test void shouldCursorHome() {
        setReportsWithTwoUpdates();
        model.cursorDown();

        model.cursorHome();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldCursorEnd() {
        setReportsWithTwoUpdates();

        model.cursorEnd();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldCycleTabsBackward() {
        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.MESSAGES);

        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.DIFF);

        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.BUILD);

        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.PLUGINS);

        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.DEPENDENCIES);
    }

    @Test void shouldBeDirtyAfterSetPhase() {
        model.clearNeedsRedraw();
        model.setPhase(Phase.SCANNING);
        then(model.needsRedraw()).isTrue();
    }

    @Test void shouldBeDirtyAfterSetReports() {
        model.clearNeedsRedraw();
        setReportsWithTwoUpdates();
        then(model.needsRedraw()).isTrue();
    }

    @Test void shouldBeDirtyAfterScanProgress() {
        model.clearNeedsRedraw();
        model.updateScanProgress(1, 5, "org:lib");
        then(model.needsRedraw()).isTrue();
    }

    @Test void shouldBeDirtyAfterBuildOutput() {
        model.clearNeedsRedraw();
        model.addBuildOutputLine("line");
        then(model.needsRedraw()).isTrue();
    }

    @Test void shouldClearNeedsRedraw() {
        model.setPhase(Phase.SCANNING);
        model.clearNeedsRedraw();
        then(model.needsRedraw()).isFalse();
    }

    @Test void shouldCollectLogMessages() {
        model.addLogMessage(new LogMessage(WARNING, "msg1"));
        model.addLogMessage(new LogMessage(WARNING, "msg2"));

        then(model.logMessages()).extracting(LogMessage::message).containsExactly("msg1", "msg2");
    }

    @Test void shouldClearLogMessages() {
        model.addLogMessage(new LogMessage(WARNING, "msg1"));

        model.clearLogMessages();

        then(model.logMessages()).isEmpty();
    }

    @Test void shouldReturnEmptyForArtifactWithoutMessages() {
        var lib = new ArtifactRef("org.example", "lib");

        then(model.worstLogLevelFor(lib)).isEmpty();
    }

    @Test void shouldReturnWarningForArtifactWithWarning() {
        var lib = new ArtifactRef("org.example", "lib");
        model.addLogMessage(new LogMessage(WARNING, "warn", lib, null));

        then(model.worstLogLevelFor(lib)).hasValue(WARNING);
    }

    @Test void shouldReturnErrorForArtifactWithError() {
        var lib = new ArtifactRef("org.example", "lib");
        model.addLogMessage(new LogMessage(ERROR, "err", lib, null));

        then(model.worstLogLevelFor(lib)).hasValue(ERROR);
    }

    @Test void shouldReturnErrorWhenBothWarningAndError() {
        var lib = new ArtifactRef("org.example", "lib");
        model.addLogMessage(new LogMessage(WARNING, "warn", lib, null));
        model.addLogMessage(new LogMessage(ERROR, "err", lib, null));

        then(model.worstLogLevelFor(lib)).hasValue(ERROR);
    }

    @Test void shouldIgnoreInfoMessagesInWorstLevel() {
        var lib = new ArtifactRef("org.example", "lib");
        model.addLogMessage(new LogMessage(INFO, "info", lib, null));

        then(model.worstLogLevelFor(lib)).isEmpty();
    }

    @Test void shouldDetectLogMessagesForArtifact() {
        var lib = new ArtifactRef("org.example", "lib");
        model.addLogMessage(new LogMessage(WARNING, "problem", lib, null));

        then(model.hasLogMessagesFor(lib)).isTrue();
        then(model.hasLogMessagesFor(new ArtifactRef("org.example", "other"))).isFalse();
    }

    @Test void shouldFilterLogMessagesByArtifact() {
        var lib = new ArtifactRef("org.example", "lib");
        model.addLogMessage(new LogMessage(WARNING, "problem1", lib, null));
        model.addLogMessage(new LogMessage(WARNING, "problem2", new ArtifactRef("org.example", "other"), null));
        model.addLogMessage(new LogMessage(WARNING, "general problem"));

        then(model.logMessagesFor(lib))
                .extracting(LogMessage::message).containsExactly("problem1");
    }

    @Test void shouldBeDirtyAfterLogMessage() {
        model.clearNeedsRedraw();
        model.addLogMessage(new LogMessage(WARNING, "msg"));
        then(model.needsRedraw()).isTrue();
    }

    @Test void shouldNotToggleSelectNoneUpdate() {
        setReportsWithMixedUpdates();
        model.setShowAll(true);
        model.cursorDown(); // move to the none update

        model.toggleSelection();
        then(model.selectedCount()).isEqualTo(0);
    }

    @Test void shouldNotSelectAllNoneUpdates() {
        setReportsWithMixedUpdates();
        model.setShowAll(true);

        model.selectAll();
        then(model.selectedCount()).isEqualTo(1);
    }

    @Test void shouldSetDiffOutput() {
        model.setDiffOutput(List.of("line 1", "line 2"));

        then(model.diffOutputLines()).containsExactly("line 1", "line 2");
    }

    @Test void shouldBeDirtyAfterSetDiffOutput() {
        model.clearNeedsRedraw();
        model.setDiffOutput(List.of("diff"));
        then(model.needsRedraw()).isTrue();
    }

    @Test void shouldKeepCursorOnSameDependencyWhenTogglingShowAllOff() {
        setReportsWithNoneMajorNone();
        model.setShowAll(true);
        model.cursorDown(); // cursor at 1 = major update

        model.toggleShowAll(); // show-all off → filtered list has only the major update at index 0

        then(model.cursor()).isEqualTo(0);
        then(model.activeUpdates().getFirst().artifactId()).isEqualTo("junit-jupiter");
    }

    @Test void shouldMoveCursorToPrecedingVisibleDependencyWhenFilteredOut() {
        setReportsWithMajorNoneNoneMajor();
        model.setShowAll(true);
        model.cursorDown(); // cursor at 1 = first none

        model.toggleShowAll(); // filtered list = [major1, major2]; preceding visible = major1 at 0

        then(model.cursor()).isEqualTo(0);
        then(model.activeUpdates().getFirst().artifactId()).isEqualTo("junit-jupiter");
    }

    @Test void shouldMoveCursorToFirstWhenNoPrecedingVisibleDependency() {
        setReportsWithNoneMajorNone();
        model.setShowAll(true);
        // cursor at 0 = up-to-date-a (none), no preceding visible item

        model.toggleShowAll();

        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldDefaultShowAllToFalse() {
        then(model.showAll()).isFalse();
    }

    @Test void shouldFilterOutUpToDateWhenShowAllIsFalse() {
        setReportsWithMixedUpdates();

        then(model.activeUpdates()).hasSize(1);
        then(model.activeUpdates().getFirst().updateType()).isEqualTo(UpdateType.major);
    }

    @Test void shouldShowNoneUpdateWithWarnings() {
        setReportsWithMixedUpdates();
        var upToDate = new ArtifactRef("com.example", "up-to-date");
        model.addLogMessage(new LogMessage(WARNING, "problem", upToDate, null));

        then(model.activeUpdates()).hasSize(2);
    }

    @Test void shouldShowAllAfterToggle() {
        setReportsWithMixedUpdates();

        model.toggleShowAll();

        then(model.showAll()).isTrue();
        then(model.activeUpdates()).hasSize(2);
    }

    @Test void shouldSetShowAll() {
        model.setShowAll(true);
        then(model.showAll()).isTrue();
    }

    @Test void shouldShowPickerMenuWhenVersionPickerOpen() {
        setReportsWithTwoUpdates();
        wireBindings();
        model.openVersionPicker();

        then(model.menuText())
                .contains("[Enter] confirm", "[Esc] cancel", "▲▼ navigate")
                .doesNotContain("[Space] toggle");
    }

    @Test void shouldShowDependencyMenuOnDependenciesTab() {
        setReportsWithTwoUpdates();
        wireBindings();

        then(model.menuText()).contains("[Space] toggle", "[Enter] pick version", "[s]how all", "[p]lugins");
    }

    @Test void shouldHidePluginsHintOnPluginsTab() {
        setReportsWithDependenciesAndPlugins();
        wireBindings();
        model.setActiveTab(Tab.PLUGINS);

        then(model.menuText()).doesNotContain("[p]lugins");
    }

    @Test void shouldHideDependencyActionsOnBuildTab() {
        setReportsWithTwoUpdates();
        wireBindings();
        model.setActiveTab(Tab.BUILD);

        then(model.menuText())
                .doesNotContain("[Space] toggle")
                .doesNotContain("[Enter] pick version")
                .doesNotContain("[s]how all");
    }

    @Test void shouldShowGlobalActionsOnAllTabs() {
        setReportsWithTwoUpdates();
        wireBindings();
        model.setActiveTab(Tab.BUILD);

        then(model.menuText()).contains("[b]uild", "[r]escan", "Tab/[ ]/◁▷ tabs", "[q/Esc] quit");
    }

    @Test void shouldShowDiffHintOnNonDiffTab() {
        setReportsWithTwoUpdates();
        wireBindings();

        then(model.menuText()).contains("[d]iff");
    }

    @Test void shouldShowDependenciesHintOnDiffTab() {
        setReportsWithTwoUpdates();
        wireBindings();
        model.setActiveTab(Tab.DIFF);

        then(model.menuText()).contains("[d]ependencies");
    }

    @Test void shouldReturnNoUpdatesMessageWhenAllUpToDate() {
        setReportsWithNoUpdates();

        then(model.emptyMessage()).isEqualTo("no updates available");
    }

    @Test void shouldReturnNullEmptyMessageWhenUpdatesExist() {
        setReportsWithTwoUpdates();

        then(model.emptyMessage()).isNull();
    }

    @Test void shouldReturnNoDependenciesMessageWhenShowAllAndNone() {
        setEmptyReports();
        model.setShowAll(true);

        then(model.emptyMessage()).isEqualTo("no dependencies");
    }

    @Test void shouldReturnNoPluginsMessageWhenShowAllAndNone() {
        setEmptyReports();
        model.setShowAll(true);
        model.setActiveTab(Tab.PLUGINS);

        then(model.emptyMessage()).isEqualTo("no plugins");
    }

    private void setReportsWithMajorNoneNoneMajor() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.example", "up-to-date-a",
                Version.fromString("1.0.0"), DEFAULT, null);
        var dep3 = new Dependency(dependency, "com.example", "up-to-date-b",
                Version.fromString("2.0.0"), DEFAULT, null);
        var dep4 = new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind",
                Version.fromString("2.20.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("1.0.0"),
                List.of(Version.fromString("1.0.0")), UpdateType.none);
        var update3 = dep3.toUpdate(Version.fromString("2.0.0"),
                List.of(Version.fromString("2.0.0")), UpdateType.none);
        var update4 = dep4.toUpdate(Version.fromString("2.21.0"),
                List.of(Version.fromString("2.20.0"), Version.fromString("2.21.0")), UpdateType.minor);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2, update3, update4), List.of(), 4);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    private void setReportsWithNoUpdates() {
        var dep = new Dependency(dependency, "com.example", "up-to-date",
                Version.fromString("1.0.0"), DEFAULT, null);
        var update = dep.toUpdate(Version.fromString("1.0.0"),
                List.of(Version.fromString("1.0.0")), UpdateType.none);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update), List.of(), 1);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    private void setEmptyReports() {
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(), List.of(), 0);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    private void setReportsWithMixedUpdates() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.example", "up-to-date",
                Version.fromString("1.0.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("1.0.0"),
                List.of(Version.fromString("1.0.0")), UpdateType.none);

        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    private void setReportsWithDependenciesAndPlugins() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind",
                Version.fromString("2.20.0"), DEFAULT, null);
        var plugin = new Dependency(Dependency.DependencyType.plugin, "org.apache.maven.plugins", "maven-compiler-plugin",
                Version.fromString("3.12.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("2.21.0"),
                List.of(Version.fromString("2.20.0"), Version.fromString("2.21.0")), UpdateType.minor);
        var pluginUpdate = plugin.toUpdate(Version.fromString("3.13.0"),
                List.of(Version.fromString("3.12.0"), Version.fromString("3.13.0")), UpdateType.minor);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2), List.of(pluginUpdate), 3);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    @Test void shouldShowSiblingAsSelectedWhenSharingVersionProperty() {
        setReportsWithSharedProperty();
        var updates = model.activeUpdates();

        model.toggleSelection(); // select first dep (uses shared.version)

        then(model.isSelected(updates.get(0))).isTrue();
        then(model.isSelected(updates.get(1))).isTrue(); // sibling with same property
    }

    @Test void shouldDeselectSiblingWhenTogglingSelectedPropertyDep() {
        setReportsWithSharedProperty();
        var updates = model.activeUpdates();
        model.toggleSelection(); // select first dep

        model.cursorDown();
        model.toggleSelection(); // toggle second dep (appears selected via sibling)

        then(model.isSelected(updates.get(0))).isFalse();
        then(model.isSelected(updates.get(1))).isFalse();
    }

    @Test void shouldShowCustomVersionOnSiblingWhenPickingSharedPropertyDep() {
        setReportsWithSharedProperty();
        var updates = model.activeUpdates();
        var picked = Version.fromString("1.5.0");

        model.setCustomVersion(updates.get(0), picked);
        model.toggleSelection();

        then(model.currentVersion(updates.get(1))).isEqualTo(picked);
    }

    @Test void shouldSetCorrectUpdateTypeOnSiblingWhenPickingSharedPropertyDep() {
        setReportsWithSharedProperty();
        var updates = model.activeUpdates();
        var picked = Version.fromString("0.5.0");

        model.setCustomVersion(updates.get(0), picked);

        var effective = model.effectiveUpdate(updates.get(1));
        then(effective.updateType()).isEqualTo(UpdateType.major); // 1.0.0 → 0.5.0 downgrade
    }

    @Test void shouldReturnCorrectPomUpdateForSiblingWithSharedProperty() {
        setReportsWithSharedProperty();
        var updates = model.activeUpdates();
        var picked = Version.fromString("1.5.0");
        model.setCustomVersion(updates.getFirst(), picked);
        model.selectAll();

        var selected = model.selectedUpdates().toList();

        var sibling = selected.stream()
                .filter(u -> u.artifactId().equals("lib-b"))
                .findFirst().orElseThrow();
        then(sibling.currentVersion()).isEqualTo(Version.fromString("1.0.0")); // original for Pom.Updater to find
        then(sibling.latestVersion()).isEqualTo(picked); // target for Pom.Updater to replace with
    }

    @Test void shouldPropagateCustomVersionToPluginSibling() {
        setReportsWithSharedPropertyAcrossTypes();
        var dep = model.activeUpdates().getFirst(); // dependency tab
        var picked = Version.fromString("1.5.0");

        model.setCustomVersion(dep, picked);
        model.toggleSelection();
        model.setActiveTab(Tab.PLUGINS);
        var plugin = model.activeUpdates().getFirst();

        then(model.currentVersion(plugin)).isEqualTo(picked);
        then(model.isSelected(plugin)).isTrue();
    }

    @Test void shouldNotAffectDepsWithDifferentProperty() {
        setReportsWithSharedProperty();
        model.setShowAll(true);
        var updates = model.activeUpdates();

        model.toggleSelection(); // select first dep (shared.version)

        then(model.isSelected(updates.get(2))).isFalse(); // no property, not affected
    }

    private void setReportsWithSharedPropertyAcrossTypes() {
        var dep = new Dependency(dependency, "com.example", "lib-a",
                Version.fromString("1.0.0"), DEFAULT, "shared.version");
        var plugin = new Dependency(Dependency.DependencyType.plugin, "com.example", "plugin-a",
                Version.fromString("1.0.0"), DEFAULT, "shared.version");
        var depUpdate = dep.toUpdate(Version.fromString("2.0.0"),
                List.of(Version.fromString("1.0.0"), Version.fromString("1.5.0"), Version.fromString("2.0.0")), UpdateType.major);
        var pluginUpdate = plugin.toUpdate(Version.fromString("2.0.0"),
                List.of(Version.fromString("1.0.0"), Version.fromString("1.5.0"), Version.fromString("2.0.0")), UpdateType.major);

        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(depUpdate), List.of(pluginUpdate), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    private void setReportsWithSharedProperty() {
        var dep1 = new Dependency(dependency, "com.example", "lib-a",
                Version.fromString("1.0.0"), DEFAULT, "shared.version");
        var dep2 = new Dependency(dependency, "com.example", "lib-b",
                Version.fromString("1.0.0"), DEFAULT, "shared.version");
        var dep3 = new Dependency(dependency, "com.other", "unrelated",
                Version.fromString("2.0.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("2.0.0"),
                List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("2.0.0"),
                List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var update3 = dep3.toUpdate(Version.fromString("2.0.0"),
                List.of(Version.fromString("2.0.0")), UpdateType.none);

        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2, update3), List.of(), 3);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    private void setReportsWithNoneMajorNone() {
        var dep1 = new Dependency(dependency, "com.example", "up-to-date-a",
                Version.fromString("1.0.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep3 = new Dependency(dependency, "com.example", "up-to-date-b",
                Version.fromString("2.0.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("1.0.0"),
                List.of(Version.fromString("1.0.0")), UpdateType.none);
        var update2 = dep2.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update3 = dep3.toUpdate(Version.fromString("2.0.0"),
                List.of(Version.fromString("2.0.0")), UpdateType.none);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2, update3), List.of(), 3);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    @Test void shouldHideManagedConsumerWhenEditableUpstreamHasUpdate() {
        var management = new Dependency(dependency,
                new com.github.t1.mavendep.domain.Coordinates("com.example", "lib", Version.fromString("1.0.0")),
                DEFAULT,
                null,
                null,
                dependencyManagement)
                .toUpdate(Version.fromString("2.0.0"), List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var consumer = new Dependency(dependency, "com.example", "lib", null, DEFAULT, null)
                .toUpdate(Version.fromString("1.0.0"), Version.fromString("2.0.0"), List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(management, consumer), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);

        then(model.activeUpdates()).containsExactly(management);
        then(model.focusedUpdateHasUpstream()).isFalse();
    }

    @Test void shouldShowManagedConsumerWhenEditableUpstreamHasNoUpdate() {
        var management = new Dependency(dependency,
                new com.github.t1.mavendep.domain.Coordinates("com.example", "lib", Version.fromString("1.0.0")),
                DEFAULT,
                null,
                null,
                dependencyManagement)
                .toUpdate(Version.fromString("1.0.0"), List.of(Version.fromString("1.0.0")), UpdateType.none);
        var consumer = new Dependency(dependency, "com.example", "lib", null, DEFAULT, null)
                .toUpdate(Version.fromString("1.0.0"), Version.fromString("2.0.0"), List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(management, consumer), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);

        then(model.activeUpdates()).containsExactly(consumer);
        then(model.focusedUpdateHasUpstream()).isTrue();
    }

    @Test void shouldShowExternallyManagedConsumerWhenItHasUpdate() {
        var consumer = new Dependency(dependency, "com.example", "lib", null, DEFAULT, null)
                .toUpdate(Version.fromString("1.0.0"), Version.fromString("2.0.0"), List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(consumer), List.of(), 1);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);

        then(model.activeUpdates()).containsExactly(consumer);
        then(model.focusedUpdateHasUpstream()).isFalse();
    }

    @Test void shouldFocusUpstreamAndRevealIt() {
        var management = new Dependency(dependency,
                new com.github.t1.mavendep.domain.Coordinates("com.example", "lib", Version.fromString("1.0.0")),
                DEFAULT,
                null,
                null,
                dependencyManagement)
                .toUpdate(Version.fromString("1.0.0"), List.of(Version.fromString("1.0.0")), UpdateType.none);
        var consumer = new Dependency(dependency, "com.example", "lib", null, DEFAULT, null)
                .toUpdate(Version.fromString("1.0.0"), Version.fromString("2.0.0"), List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(management, consumer), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);

        model.focusUpstream();

        then(model.showAll()).isTrue();
        then(model.activeUpdates()).containsExactly(management, consumer);
        then(model.focusedUpdate()).isEqualTo(management);
    }

    @Test void shouldDisplayRootProjectNameInTitle() {
        var rootPom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(rootPom.path()).willReturn(Path.of("pom.xml"));
        given(rootPom.coordinates()).willReturn(new com.github.t1.mavendep.domain.Coordinates("com.example", "root-project", Version.fromString("1.0.0")));
        given(rootPom.name()).willReturn("Root Project");
        var modulePom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(modulePom.path()).willReturn(Path.of("module/pom.xml"));
        given(modulePom.coordinates()).willReturn(new com.github.t1.mavendep.domain.Coordinates("com.example", "module-project", Version.fromString("1.0.0")));
        given(modulePom.name()).willReturn("Module Project");
        model.setRootPomFiles(List.of(Path.of("pom.xml")));
        model.setReports(List.of(
                new ProjectReport(rootPom, Optional.empty(), List.of(), List.of(), 0),
                new ProjectReport(modulePom, Optional.empty(), List.of(), List.of(), 0)));

        then(model.titleText()).isEqualTo("Maven Dependency Manager — Root Project");
    }

    @Test void shouldFallBackToRootArtifactIdWhenNameMissing() {
        var rootPom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(rootPom.path()).willReturn(Path.of("pom.xml"));
        given(rootPom.coordinates()).willReturn(new com.github.t1.mavendep.domain.Coordinates("com.example", "root-project", Version.fromString("1.0.0")));
        given(rootPom.name()).willReturn(null);
        model.setRootPomFiles(List.of(Path.of("pom.xml")));
        model.setReports(List.of(new ProjectReport(rootPom, Optional.empty(), List.of(), List.of(), 0)));

        then(model.titleText()).isEqualTo("Maven Dependency Manager — root-project");
    }

    @Test void shouldDisplayMultipleRootProjectNamesInTitle() {
        var firstPom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(firstPom.path()).willReturn(Path.of("first/pom.xml"));
        given(firstPom.coordinates()).willReturn(new com.github.t1.mavendep.domain.Coordinates("com.example", "first-project", Version.fromString("1.0.0")));
        given(firstPom.name()).willReturn("First Project");
        var secondPom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(secondPom.path()).willReturn(Path.of("second/pom.xml"));
        given(secondPom.coordinates()).willReturn(new com.github.t1.mavendep.domain.Coordinates("com.example", "second-project", Version.fromString("1.0.0")));
        given(secondPom.name()).willReturn("Second Project");
        model.setRootPomFiles(List.of(Path.of("first/pom.xml"), Path.of("second/pom.xml")));
        model.setReports(List.of(
                new ProjectReport(firstPom, Optional.empty(), List.of(), List.of(), 0),
                new ProjectReport(secondPom, Optional.empty(), List.of(), List.of(), 0)));

        then(model.titleText()).isEqualTo("Maven Dependency Manager — First Project, Second Project");
    }

    @Test void shouldGroupUpdatesByPom() {
        setReportsFromTwoPoms();

        var grouped = model.activeGroupedUpdates();

        then(grouped).hasSize(2);
        then(grouped.getFirst().getKey()).isEqualTo(Path.of("pom.xml"));
        then(grouped.getFirst().getValue()).hasSize(1);
        then(grouped.getFirst().getValue().getFirst().artifactId()).isEqualTo("junit-jupiter");
        then(grouped.get(1).getKey()).isEqualTo(Path.of("sub/pom.xml"));
        then(grouped.get(1).getValue()).hasSize(1);
        then(grouped.get(1).getValue().getFirst().artifactId()).isEqualTo("jackson-databind");
    }

    @Test void shouldFilterGroupedUpdatesByShowAll() {
        setReportsFromTwoPomsWithMixed();

        var grouped = model.activeGroupedUpdates();

        // only the pom with a real update should appear (show-all is off by default)
        then(grouped).hasSize(1);
        then(grouped.getFirst().getKey()).isEqualTo(Path.of("pom.xml"));
    }

    @Test void shouldIncludeAllGroupsWhenShowAllIsOn() {
        setReportsFromTwoPomsWithMixed();
        model.setShowAll(true);

        var grouped = model.activeGroupedUpdates();

        then(grouped).hasSize(2);
    }

    @Test void shouldReturnSingleGroupForSinglePom() {
        setReportsWithTwoUpdates();

        var grouped = model.activeGroupedUpdates();

        then(grouped).hasSize(1);
        then(grouped.getFirst().getValue()).hasSize(2);
    }

    @Test void shouldPreserveFlatIndexAcrossGroups() {
        setReportsFromTwoPoms();

        var flat = model.activeUpdates();

        then(flat).hasSize(2);
        then(flat.get(0).artifactId()).isEqualTo("junit-jupiter");
        then(flat.get(1).artifactId()).isEqualTo("jackson-databind");
    }

    private void setReportsFromTwoPoms() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind",
                Version.fromString("2.20.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("2.21.0"),
                List.of(Version.fromString("2.20.0"), Version.fromString("2.21.0")), UpdateType.minor);

        var pom1 = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom1.path()).willReturn(Path.of("pom.xml"));
        var pom2 = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom2.path()).willReturn(Path.of("sub/pom.xml"));
        var report1 = new ProjectReport(pom1, Optional.empty(), List.of(update1), List.of(), 1);
        var report2 = new ProjectReport(pom2, Optional.empty(), List.of(update2), List.of(), 1);
        model.setReports(List.of(report1, report2));
        model.setPhase(Phase.READY);
    }

    private void setReportsFromTwoPomsWithMixed() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.example", "up-to-date",
                Version.fromString("1.0.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("1.0.0"),
                List.of(Version.fromString("1.0.0")), UpdateType.none);

        var pom1 = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom1.path()).willReturn(Path.of("pom.xml"));
        var pom2 = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom2.path()).willReturn(Path.of("sub/pom.xml"));
        var report1 = new ProjectReport(pom1, Optional.empty(), List.of(update1), List.of(), 1);
        var report2 = new ProjectReport(pom2, Optional.empty(), List.of(update2), List.of(), 1);
        model.setReports(List.of(report1, report2));
        model.setPhase(Phase.READY);
    }

    private void setReportsWithCommittedVersion() {
        var dep = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var update = dep.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major)
                .withCommittedVersion(Version.fromString("5.9.0"));
        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update), List.of(), 1);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    /// Creates a [DashboardController] to wire bindings into the model.
    private void wireBindings() {
        new DashboardController(model, () -> {}, () -> {}, () -> {}, () -> {});
    }

    private void setReportsWithTwoUpdates() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind",
                Version.fromString("2.20.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("2.21.0"),
                List.of(Version.fromString("2.20.0"), Version.fromString("2.21.0")), UpdateType.minor);

        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(pom.path()).willReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }
}
