package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
import com.github.t1.mavendep.tui.DashboardModel.Phase;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.DEFAULT;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock TuiRunner runner;

    private final DashboardModel model = new DashboardModel();
    private final AtomicBoolean updateCalled = new AtomicBoolean();
    private final AtomicBoolean buildCalled = new AtomicBoolean();
    private final AtomicBoolean rescanCalled = new AtomicBoolean();
    private final AtomicBoolean diffCalled = new AtomicBoolean();
    private DashboardController controller;

    @BeforeEach void setUp() {
        controller = new DashboardController(model,
                () -> updateCalled.set(true),
                () -> buildCalled.set(true),
                () -> rescanCalled.set(true),
                () -> diffCalled.set(true));
        setUpReady();
    }

    @Test void shouldQuitOnQ() {
        controller.handle(KeyEvent.ofChar('q'), runner);
        verify(runner).quit();
    }

    @Test void shouldQuitOnEscape() {
        controller.handle(KeyEvent.ofKey(KeyCode.ESCAPE), runner);
        verify(runner).quit();
    }

    @Test void shouldNavigateDown() {
        var result = controller.handle(KeyEvent.ofKey(KeyCode.DOWN), runner);
        then(result).isTrue();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldNavigateUp() {
        model.cursorDown();
        var result = controller.handle(KeyEvent.ofKey(KeyCode.UP), runner);
        then(result).isTrue();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldNavigatePageDown() {
        var result = controller.handle(KeyEvent.ofKey(KeyCode.PAGE_DOWN), runner);
        then(result).isTrue();
        then(model.cursor()).isEqualTo(1);
    }

    @Test void shouldNavigatePageUp() {
        model.cursorEnd();

        var result = controller.handle(KeyEvent.ofKey(KeyCode.PAGE_UP), runner);
        then(result).isTrue();
        then(model.cursor()).isEqualTo(0);
    }

    @Test void shouldToggleSelectionAndAutoApply() {
        var result = controller.handle(KeyEvent.ofChar(' '), runner);
        then(result).isTrue();
        then(model.isSelected(model.activeUpdates().getFirst())).isTrue();
        then(updateCalled).isTrue();
    }

    @Test void shouldSelectAllAndAutoApply() {
        controller.handle(KeyEvent.ofChar('a'), runner);
        then(model.selectedCount()).isEqualTo(2);
        then(updateCalled).isTrue();
    }

    @Test void shouldDeselectAllWhenAllSelectedAndAutoApply() {
        model.selectAll();
        controller.handle(KeyEvent.ofChar('a'), runner);
        then(model.selectedCount()).isEqualTo(0);
        then(updateCalled).isTrue();
    }

    @Test void shouldSelectNoneAndAutoApply() {
        model.selectAll();
        controller.handle(KeyEvent.ofChar('n'), runner);
        then(model.selectedCount()).isEqualTo(0);
        then(updateCalled).isTrue();
    }

    @Test void shouldTriggerBuild() {
        controller.handle(KeyEvent.ofChar('b'), runner);
        then(buildCalled).isTrue();
    }

    @Test void shouldTriggerRescan() {
        controller.handle(KeyEvent.ofChar('r'), runner);
        then(rescanCalled).isTrue();
    }

    @Test void shouldJumpToDiffTabAndRefresh() {
        controller.handle(KeyEvent.ofChar('d'), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.DIFF);
        then(diffCalled).isTrue();
    }

    @Test void shouldJumpBackToDependenciesWhenPressingDOnDiffTab() {
        model.setActiveTab(DashboardModel.Tab.DIFF);

        controller.handle(KeyEvent.ofChar('d'), runner);

        then(model.activeTab()).isEqualTo(DashboardModel.Tab.DEPENDENCIES);
    }

    @Test void shouldJumpToPluginsTab() {
        controller.handle(KeyEvent.ofChar('p'), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.PLUGINS);
    }

    @Test void shouldJumpToMessagesTab() {
        controller.handle(KeyEvent.ofChar('m'), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.MESSAGES);
    }

    @Test void shouldSwitchTabs() {
        controller.handle(KeyEvent.ofKey(KeyCode.TAB), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.PLUGINS);
        then(diffCalled).isFalse();
    }

    @Test void shouldSwitchTabsBackwardWithShiftTab() {
        controller.handle(KeyEvent.ofKey(KeyCode.TAB, dev.tamboui.tui.event.KeyModifiers.SHIFT), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.MESSAGES);
    }

    @Test void shouldSwitchTabsBackwardWithBackTabWorkaround() {
        // BackTabBackendWrapper translates ESC [ Z into UNKNOWN + SHIFT
        controller.handle(KeyEvent.ofKey(KeyCode.UNKNOWN, dev.tamboui.tui.event.KeyModifiers.SHIFT), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.MESSAGES);
    }

    @Test void shouldSwitchTabsForwardWithBracket() {
        controller.handle(KeyEvent.ofChar(']'), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.PLUGINS);
    }

    @Test void shouldSwitchTabsBackwardWithBracket() {
        controller.handle(KeyEvent.ofChar('['), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.MESSAGES);
    }

    @Test void shouldSwitchTabsForwardWithRightArrow() {
        controller.handle(KeyEvent.ofKey(KeyCode.RIGHT), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.PLUGINS);
    }

    @Test void shouldSwitchTabsBackwardWithLeftArrow() {
        controller.handle(KeyEvent.ofKey(KeyCode.LEFT), runner);
        then(model.activeTab()).isEqualTo(DashboardModel.Tab.MESSAGES);
    }

    @Test void shouldOpenVersionPicker() {
        controller.handle(KeyEvent.ofKey(KeyCode.ENTER), runner);
        then(model.isVersionPickerOpen()).isTrue();
    }

    @Test void shouldIgnoreKeysWhileScanning() {
        model.setPhase(Phase.SCANNING);
        var result = controller.handle(KeyEvent.ofChar(' '), runner);
        then(result).isFalse();
    }

    @Test void shouldRedrawOnTickWhenDirty() {
        model.updateScanProgress(1, 5, "org:lib");
        var result = controller.handle(new TickEvent(0, java.time.Duration.ZERO), runner);
        then(result).isTrue();
    }

    @Test void shouldClearDirtyAfterTickRedraw() {
        model.updateScanProgress(1, 5, "org:lib");
        controller.handle(new TickEvent(0, java.time.Duration.ZERO), runner);
        var result = controller.handle(new TickEvent(0, java.time.Duration.ZERO), runner);
        then(result).isFalse();
    }

    @Test void shouldNotRedrawOnTickWhenClean() {
        model.clearNeedsRedraw(); // setUp sets reports which marks dirty
        var result = controller.handle(new TickEvent(0, java.time.Duration.ZERO), runner);
        then(result).isFalse();
    }

    @Test void shouldAutoApplyOnVersionPickerConfirm() {
        model.openVersionPicker();
        controller.handle(KeyEvent.ofKey(KeyCode.UP), runner);
        controller.handle(KeyEvent.ofKey(KeyCode.ENTER), runner);
        then(model.isVersionPickerOpen()).isFalse();
        then(updateCalled).isTrue();
    }

    @Test void shouldCloseVersionPickerOnEscape() {
        model.openVersionPicker();
        controller.handle(KeyEvent.ofKey(KeyCode.ESCAPE), runner);
        then(model.isVersionPickerOpen()).isFalse();
    }

    private void setUpReady() {
        var dep1 = new Dependency(dependency, "org.junit.jupiter", "junit-jupiter",
                Version.fromString("5.10.0"), DEFAULT, null);
        var dep2 = new Dependency(dependency, "com.fasterxml", "jackson-databind",
                Version.fromString("2.20.0"), DEFAULT, null);
        var update1 = dep1.toUpdate(Version.fromString("6.0.3"),
                List.of(Version.fromString("5.10.0"), Version.fromString("6.0.3")), UpdateType.major);
        var update2 = dep2.toUpdate(Version.fromString("2.21.0"),
                List.of(Version.fromString("2.20.0"), Version.fromString("2.21.0")), UpdateType.minor);

        var pom = mock(com.github.t1.mavendep.domain.Pom.class);
        lenient().doReturn(Path.of("pom.xml")).when(pom).path();
        var report = new ProjectReport(pom, Optional.empty(), List.of(update1, update2), List.of(), 2);
        model.setReports(List.of(report));
        model.setPhase(Phase.READY);
        model.clearNeedsRedraw();
    }
}

class ApplyUpdatesActionTest {
    private final DashboardModel model = new DashboardModel();

    @Test void shouldApplyPickedVersionOnlyToSelectedPom() {
        var firstPom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(firstPom.path()).willReturn(Path.of("pom.xml"));
        var secondPom = mock(com.github.t1.mavendep.domain.Pom.class);
        given(secondPom.path()).willReturn(Path.of("sub/pom.xml"));

        var firstUpdate = new Dependency(dependency, "com.example", "managed-lib", null, DEFAULT, null)
                .toUpdate(Version.fromString("1.0.0"), Version.fromString("2.0.0"),
                        List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);
        var secondUpdate = new Dependency(dependency, "com.example", "managed-lib", null, DEFAULT, null)
                .toUpdate(Version.fromString("1.0.0"), Version.fromString("2.0.0"),
                        List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")), UpdateType.major);

        model.setReports(List.of(
                new ProjectReport(firstPom, Optional.empty(), List.of(firstUpdate), List.of(), 1),
                new ProjectReport(secondPom, Optional.empty(), List.of(secondUpdate), List.of(), 1)));
        model.setPhase(Phase.READY);
        model.openVersionPicker();
        model.versionPickerUp();
        model.confirmVersionPick();

        var firstApplied = new AtomicReference<List<Update>>();
        var secondApplied = new AtomicReference<List<Update>>();
        doAnswer(invocation -> {
            firstApplied.set(updatesFrom(invocation.getArgument(0)));
            return null;
        }).when(firstPom).apply(org.mockito.ArgumentMatchers.any());
        doAnswer(invocation -> {
            secondApplied.set(updatesFrom(invocation.getArgument(0)));
            return null;
        }).when(secondPom).apply(org.mockito.ArgumentMatchers.any());

        new ApplyUpdatesAction(model).run();

        then(firstApplied.get()).singleElement().extracting(Update::artifactId).isEqualTo("managed-lib");
        then(firstApplied.get().getFirst().latestVersion()).isEqualTo(Version.fromString("2.0.0"));
        then(secondApplied.get()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Update> updatesFrom(Object argument) {
        return ((java.util.stream.Stream<Update>) argument).toList();
    }
}
