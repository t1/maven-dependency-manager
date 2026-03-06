package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Tab;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/// A single key binding entry for the TUI menu,
/// serving as the source of truth for key matching, action, and display.
class MenuBinding {

    private final Predicate<KeyEvent> matcher;
    private final Function<KeyEvent, Boolean> action;
    private final Function<Tab, Optional<String>> displayFn;

    private MenuBinding(Predicate<KeyEvent> matcher, Function<KeyEvent, Boolean> action,
                        Function<Tab, Optional<String>> displayFn) {
        this.matcher = matcher;
        this.action = action;
        this.displayFn = displayFn;
    }

    boolean matches(KeyEvent key) {return matcher != null && matcher.test(key);}

    boolean activate(KeyEvent key) {return action != null && action.apply(key);}

    Optional<String> displayFor(Tab tab) {return displayFn.apply(tab);}

    /// Char binding with fixed display on specific tabs.
    static MenuBinding charBinding(char ch, String display, Set<Tab> visibleOn,
                                   Function<KeyEvent, Boolean> action) {
        return new MenuBinding(
                key -> key.code() == KeyCode.CHAR && key.character() == ch,
                action,
                tab -> visibleOn.contains(tab) ? Optional.of(display) : Optional.empty());
    }

    /// Char binding with dynamic display (for tab shortcuts like `d`, `p`, `m`).
    static MenuBinding charBinding(char ch, Function<Tab, String> displayFn,
                                   Function<KeyEvent, Boolean> action) {
        return new MenuBinding(
                key -> key.code() == KeyCode.CHAR && key.character() == ch,
                action,
                tab -> Optional.ofNullable(displayFn.apply(tab)));
    }

    /// KeyCode binding with fixed display (e.g. Enter).
    static MenuBinding keyBinding(KeyCode code, String display, Set<Tab> visibleOn,
                                  Function<KeyEvent, Boolean> action) {
        return new MenuBinding(
                key -> key.code() == code,
                action,
                tab -> visibleOn.contains(tab) ? Optional.of(display) : Optional.empty());
    }

    /// Hidden char binding — no menu display.
    static MenuBinding charBinding(char ch, Function<KeyEvent, Boolean> action) {
        return new MenuBinding(
                key -> key.code() == KeyCode.CHAR && key.character() == ch,
                action,
                _ -> Optional.empty());
    }

    /// Hidden action binding — no menu display (cursor keys, tab nav).
    static MenuBinding hiddenBinding(Predicate<KeyEvent> matcher, Function<KeyEvent, Boolean> action) {
        return new MenuBinding(matcher, action, _ -> Optional.empty());
    }

    /// Display-only — no key matching or action (composite labels, quit hint).
    static MenuBinding displayOnlyBinding(String display, Set<Tab> visibleOn) {
        return new MenuBinding(null, null,
                tab -> visibleOn.contains(tab) ? Optional.of(display) : Optional.empty());
    }
}
