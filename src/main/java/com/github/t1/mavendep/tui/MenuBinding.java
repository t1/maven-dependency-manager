package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.tui.DashboardModel.Tab;

import java.util.Set;

/// A single key binding entry for the TUI menu,
/// serving as the source of truth for both display and handling.
record MenuBinding(String display, Set<Tab> tabs) {

    boolean isAvailableOn(Tab tab) {
        return tabs.contains(tab);
    }
}
