package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Phase;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;

import java.util.List;

import static com.github.t1.mavendep.tui.DashboardModel.Tab.ALL_TABS;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCIES;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DEPENDENCY_TABS;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.DIFF;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.MESSAGES;
import static com.github.t1.mavendep.tui.DashboardModel.Tab.PLUGINS;
import static com.github.t1.mavendep.tui.MenuBinding.charBinding;
import static com.github.t1.mavendep.tui.MenuBinding.displayOnlyBinding;
import static com.github.t1.mavendep.tui.MenuBinding.hiddenBinding;
import static com.github.t1.mavendep.tui.MenuBinding.keyBinding;
import static dev.tamboui.tui.event.KeyCode.DOWN;
import static dev.tamboui.tui.event.KeyCode.END;
import static dev.tamboui.tui.event.KeyCode.ENTER;
import static dev.tamboui.tui.event.KeyCode.ESCAPE;
import static dev.tamboui.tui.event.KeyCode.HOME;
import static dev.tamboui.tui.event.KeyCode.LEFT;
import static dev.tamboui.tui.event.KeyCode.PAGE_DOWN;
import static dev.tamboui.tui.event.KeyCode.PAGE_UP;
import static dev.tamboui.tui.event.KeyCode.RIGHT;
import static dev.tamboui.tui.event.KeyCode.TAB;
import static dev.tamboui.tui.event.KeyCode.UNKNOWN;
import static dev.tamboui.tui.event.KeyCode.UP;

/// Handles key events and dispatches to [DashboardModel] mutations and actions.
public class DashboardController {
    private final DashboardModel model;
    private final Runnable onDiff;
    private final List<MenuBinding> bindings;
    private final List<MenuBinding> pickerBindings;

    public DashboardController(
            DashboardModel model,
            Runnable onUpdate,
            Runnable onBuild,
            Runnable onRescan,
            Runnable onDiff) {
        this.model = model;
        this.onDiff = onDiff;
        this.bindings = List.of(
                // -- Displayed, dependency tabs --
                charBinding(' ', "[Space] toggle", DEPENDENCY_TABS, _ -> {
                    model.toggleSelection();
                    onUpdate.run();
                    return true;
                }),
                keyBinding(ENTER, "[Enter] pick version", DEPENDENCY_TABS, _ -> {
                    model.openVersionPicker();
                    return true;
                }),
                charBinding('s', "[s]how all", DEPENDENCY_TABS, _ -> {
                    model.toggleShowAll();
                    return true;
                }),
                charBinding('u', tab -> DEPENDENCY_TABS.contains(tab) && model.focusedUpdateHasUpstream() ? "[u] upstream" : null, _ -> {
                    model.focusUpstream();
                    return true;
                }),

                // -- Displayed, all tabs --
                charBinding('b', "[b]uild", ALL_TABS, _ -> {
                    onBuild.run();
                    return true;
                }),
                charBinding('r', "[r]escan", ALL_TABS, _ -> {
                    onRescan.run();
                    return true;
                }),

                // -- Tab shortcuts with dynamic display --
                charBinding('p', tab -> tab != PLUGINS ? "[p]lugins" : null, _ -> {
                    model.setActiveTab(PLUGINS);
                    return true;
                }),
                charBinding('d', tab -> tab == DIFF ? "[d]ependencies" : "[d]iff", _ -> handleDiffToggle()),
                charBinding('m', tab -> tab != MESSAGES ? "[m]essages" : null, _ -> {
                    model.setActiveTab(MESSAGES);
                    return true;
                }),

                // -- Tab navigation (composite display + hidden physical keys) --
                displayOnlyBinding("Tab/[ ]/◁▷ tabs", ALL_TABS),
                displayOnlyBinding("[q/Esc] quit", ALL_TABS),

                // -- Hidden char bindings --
                charBinding('a', _ -> {
                    model.toggleSelectAll();
                    onUpdate.run();
                    return true;
                }),
                charBinding('n', _ -> {
                    model.selectNone();
                    onUpdate.run();
                    return true;
                }),
                charBinding('[', _ -> {
                    model.previousTab();
                    refreshDiffIfActive();
                    return true;
                }),
                charBinding(']', _ -> {
                    model.nextTab();
                    refreshDiffIfActive();
                    return true;
                }),

                // -- Hidden key bindings --
                hiddenBinding(key -> key.code() == UP, _ -> {
                    model.cursorUp();
                    return true;
                }),
                hiddenBinding(key -> key.code() == DOWN, _ -> {
                    model.cursorDown();
                    return true;
                }),
                hiddenBinding(key -> key.code() == PAGE_UP, _ -> {
                    model.cursorPageUp();
                    return true;
                }),
                hiddenBinding(key -> key.code() == PAGE_DOWN, _ -> {
                    model.cursorPageDown();
                    return true;
                }),
                hiddenBinding(key -> key.code() == HOME, _ -> {
                    model.cursorHome();
                    return true;
                }),
                hiddenBinding(key -> key.code() == END, _ -> {
                    model.cursorEnd();
                    return true;
                }),
                hiddenBinding(key -> key.code() == LEFT, _ -> {
                    model.previousTab();
                    refreshDiffIfActive();
                    return true;
                }),
                hiddenBinding(key -> key.code() == RIGHT, _ -> {
                    model.nextTab();
                    refreshDiffIfActive();
                    return true;
                }),
                hiddenBinding(key -> key.code() == TAB, this::handleTab),
                hiddenBinding(key -> key.code() == UNKNOWN && key.modifiers().shift(),
                        _ -> {
                            model.previousTab();
                            refreshDiffIfActive();
                            return true;
                        })
        );
        model.setBindings(bindings);
        this.pickerBindings = List.of(
                displayOnlyBinding("▲▼ navigate", ALL_TABS),
                keyBinding(ENTER, "[Enter] confirm", ALL_TABS, _ -> {
                    model.confirmVersionPick();
                    onUpdate.run();
                    return true;
                }),
                keyBinding(ESCAPE, "[Esc] cancel", ALL_TABS, _ -> {
                    model.closeVersionPicker();
                    return true;
                }),
                hiddenBinding(KeyEvent::isUp, _ -> {
                    model.versionPickerUp();
                    return true;
                }),
                hiddenBinding(KeyEvent::isDown, _ -> {
                    model.versionPickerDown();
                    return true;
                })
        );
        model.setPickerBindings(pickerBindings);
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

        if (key.isQuit() || key.code() == ESCAPE && !model.isVersionPickerOpen()) {
            runner.quit();
            return false;
        }

        if (model.phase() != Phase.READY) return false;

        return dispatch(key, model.isVersionPickerOpen() ? pickerBindings : bindings);
    }

    private boolean dispatch(KeyEvent key, List<MenuBinding> activeBindings) {
        return activeBindings.stream()
                .filter(b -> b.matches(key))
                .findFirst()
                .map(b -> b.activate(key))
                .orElse(false);
    }

    private boolean handleTab(KeyEvent key) {
        if (key.modifiers().shift()) model.previousTab();
        else model.nextTab();
        refreshDiffIfActive();
        return true;
    }

    private boolean handleDiffToggle() {
        if (model.activeTab() == DIFF) model.setActiveTab(DEPENDENCIES);
        else {
            model.setActiveTab(DIFF);
            refreshDiffIfActive();
        }
        return true;
    }

    private void refreshDiffIfActive() {
        if (model.activeTab() == DIFF) onDiff.run();
    }

}
