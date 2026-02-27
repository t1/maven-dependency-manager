package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
import com.github.t1.mavendep.tui.DashboardModel.Phase;
import com.github.t1.mavendep.tui.DashboardModel.Tab;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.DEFAULT;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        then(model.activeTab()).isEqualTo(Tab.DEPENDENCIES);
    }

    @Test void shouldMoveCursorDown() {
        setReportsWithTwoDependencyUpdates();

        model.cursorDown();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldNotMoveCursorBelowLast() {
        setReportsWithTwoDependencyUpdates();

        model.cursorDown();
        model.cursorDown();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldMoveCursorUp() {
        setReportsWithTwoDependencyUpdates();
        model.cursorDown();

        model.cursorUp();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldNotMoveCursorAboveZero() {
        model.cursorUp();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldToggleSelection() {
        setReportsWithTwoDependencyUpdates();
        var updates = model.activeUpdates();

        model.toggleSelection();
        then(model.isSelected(updates.getFirst())).isTrue();

        model.toggleSelection();
        then(model.isSelected(updates.getFirst())).isFalse();
    }

    @Test void shouldSelectAll() {
        setReportsWithTwoDependencyUpdates();

        model.toggleSelectAll();
        then(model.selectedCount()).isEqualTo(2);
    }

    @Test void shouldDeselectAllWhenAllSelected() {
        setReportsWithTwoDependencyUpdates();
        model.selectAll();

        model.toggleSelectAll();
        then(model.selectedCount()).isEqualTo(0);
    }

    @Test void shouldSelectNone() {
        setReportsWithTwoDependencyUpdates();
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
        setReportsWithTwoDependencyUpdates();

        model.openVersionPicker();
        then(model.isVersionPickerOpen()).isTrue();

        model.closeVersionPicker();
        then(model.isVersionPickerOpen()).isFalse();
    }

    @Test void shouldSetCustomVersion() {
        setReportsWithTwoDependencyUpdates();
        var updates = model.activeUpdates();
        var original = updates.getFirst();
        var customVersion = Version.fromString("5.9.0");

        model.setCustomVersion(original, customVersion);

        var effective = model.effectiveUpdate(original);
        then(effective.latestVersion()).isEqualTo(customVersion);
    }

    @Test void shouldCursorHome() {
        setReportsWithTwoDependencyUpdates();
        model.cursorDown();

        model.cursorHome();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldCursorEnd() {
        setReportsWithTwoDependencyUpdates();

        model.cursorEnd();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldCycleTabsBackward() {
        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.BUILD);

        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.PLUGINS);

        model.previousTab();
        then(model.activeTab()).isEqualTo(Tab.DEPENDENCIES);
    }

    @Test void shouldBeDirtyAfterSetPhase() {
        model.clearDirty();
        model.setPhase(Phase.SCANNING);
        then(model.isDirty()).isTrue();
    }

    @Test void shouldBeDirtyAfterSetReports() {
        model.clearDirty();
        setReportsWithTwoDependencyUpdates();
        then(model.isDirty()).isTrue();
    }

    @Test void shouldBeDirtyAfterScanProgress() {
        model.clearDirty();
        model.updateScanProgress(1, 5, "org:lib");
        then(model.isDirty()).isTrue();
    }

    @Test void shouldBeDirtyAfterBuildOutput() {
        model.clearDirty();
        model.addBuildOutputLine("line");
        then(model.isDirty()).isTrue();
    }

    @Test void shouldClearDirty() {
        model.setPhase(Phase.SCANNING);
        model.clearDirty();
        then(model.isDirty()).isFalse();
    }

    @Test void shouldCollectLogMessages() {
        model.addLogMessage("msg1");
        model.addLogMessage("msg2");

        then(model.logMessages()).containsExactly("msg1", "msg2");
    }

    @Test void shouldBeDirtyAfterLogMessage() {
        model.clearDirty();
        model.addLogMessage("msg");
        then(model.isDirty()).isTrue();
    }

    @Test void shouldDefaultShowAllToFalse() {
        then(model.showAll()).isFalse();
    }

    @Test void shouldFilterOutUpToDateWhenShowAllIsFalse() {
        setReportsWithMixedUpdates();

        then(model.activeUpdates()).hasSize(1);
        then(model.activeUpdates().getFirst().updateType()).isEqualTo(UpdateType.major);
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
        when(pom.path()).thenReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }

    private void setReportsWithTwoDependencyUpdates() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.fasterxml.jackson.core", "jackson-databind",
                Version.fromString("2.20.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("2.21.0"),
                List.of(Version.fromString("2.20.0"), Version.fromString("2.21.0")), UpdateType.minor);

        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        when(pom.path()).thenReturn(java.nio.file.Path.of("pom.xml"));
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
    }
}
