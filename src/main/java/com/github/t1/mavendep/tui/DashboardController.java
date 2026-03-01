package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Phase;
import dev.tamboui.tui.TuiRunner;

import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCIES;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DIFF;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.PLUGINS;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;

/// Handles key events and dispatches to [DashboardModel] mutations and actions.
public class DashboardController {

    private final DashboardModel model;
    private final Runnable onUpdate;
    private final Runnable onBuild;
    private final Runnable onRescan;
    private final Runnable onDiff;

    public DashboardController(DashboardModel model, Runnable onUpdate, Runnable onBuild, Runnable onRescan,
                               Runnable onDiff) {
        this.model = model;
        this.onUpdate = onUpdate;
        this.onBuild = onBuild;
        this.onRescan = onRescan;
        this.onDiff = onDiff;
    }

    /// Handles an event. Returns true if a redraw is needed.
    public boolean handle(Event event, TuiRunner runner) {
        // Tick events trigger redraws when the model has pending background changes.
        // Background state changes (via TuiRunner.runOnRenderThread) update the model
        // but don't trigger a redraw, so we pick them up on the next tick.
        if (event instanceof TickEvent) {
            if (model.needsRedraw()) {
                model.clearNeedsRedraw();
                return true;
            }
            return false;
        }

        if (!(event instanceof KeyEvent key)) return false;

        if (key.isQuit()) {
            runner.quit();
            return false;
        }

        if (model.phase() != Phase.READY) return false;

        if (model.isVersionPickerOpen()) return handleVersionPicker(key);

        return handleMain(key);
    }

    private boolean handleMain(KeyEvent key) {
        if (key.isUp()) {
            model.cursorUp();
            return true;
        }
        if (key.isDown()) {
            model.cursorDown();
            return true;
        }
        if (key.isHome()) {
            model.cursorHome();
            return true;
        }
        if (key.isEnd()) {
            model.cursorEnd();
            return true;
        }

        if (key.isFocusPrevious() || isBackTab(key) || key.isChar('[') || key.isLeft()) {
            model.previousTab();
            refreshDiffIfActive();
            return true;
        }
        if (key.isFocusNext() || key.isChar(']') || key.isRight()) {
            model.nextTab();
            refreshDiffIfActive();
            return true;
        }

        if (key.isKey(dev.tamboui.tui.event.KeyCode.ENTER)) {
            model.openVersionPicker();
            return true;
        }
        if (key.isChar(' ')) {
            model.toggleSelection();
            onUpdate.run();
            return true;
        }
        if (key.isChar('a')) {
            model.toggleSelectAll();
            onUpdate.run();
            return true;
        }
        if (key.isChar('n')) {
            model.selectNone();
            onUpdate.run();
            return true;
        }
        if (key.isChar('s')) {
            model.toggleShowAll();
            return true;
        }
        if (key.isChar('b')) {
            onBuild.run();
            return true;
        }
        if (key.isChar('r')) {
            onRescan.run();
            return true;
        }
        if (key.isChar('p')) {
            model.setActiveTab(PLUGINS);
            return true;
        }
        if (key.isChar('d')) {
            if (model.activeTab() == DIFF) {
                model.setActiveTab(DEPENDENCIES);
            } else {
                model.setActiveTab(DIFF);
                refreshDiffIfActive();
            }
            return true;
        }

        return false;
    }

    private void refreshDiffIfActive() {
        if (model.activeTab() == DIFF) onDiff.run();
    }

    /// Detects Shift+Tab via the [BackTabBackendWrapper] workaround.
    /// TamboUI 0.1.0's EventParser doesn't handle `ESC [ Z` (back-tab),
    /// so the wrapper translates it to `ESC [ 1 ; 2 Z` which parses as
    /// `KeyCode.UNKNOWN` with `KeyModifiers.SHIFT`.
    private static boolean isBackTab(KeyEvent key) {
        return key.code() == KeyCode.UNKNOWN && key.modifiers().shift();
    }

    private boolean handleVersionPicker(KeyEvent key) {
        if (key.isUp()) {
            model.versionPickerUp();
            return true;
        }
        if (key.isDown()) {
            model.versionPickerDown();
            return true;
        }
        if (key.isConfirm()) {
            model.confirmVersionPick();
            onUpdate.run();
            return true;
        }
        if (key.isCancel()) {
            model.closeVersionPicker();
            return true;
        }
        return false;
    }
}
