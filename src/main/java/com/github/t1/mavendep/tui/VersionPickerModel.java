package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.Version;

import java.util.List;
import java.util.function.Supplier;

/// Manages the version picker overlay state within the TUI dashboard.
class VersionPickerModel {

    private final Supplier<DependencyUpdate> focusedUpdateSupplier;

    private boolean open;
    private int cursor;

    VersionPickerModel(Supplier<DependencyUpdate> focusedUpdateSupplier) {
        this.focusedUpdateSupplier = focusedUpdateSupplier;
    }

    boolean isOpen() {return open;}

    void open() {
        if (focusedUpdateSupplier.get() != null) {
            open = true;
            cursor = 0;
        }
    }

    void close() {open = false;}

    int cursor() {return cursor;}

    void cursorUp() {
        if (cursor > 0) cursor--;
    }

    void cursorDown() {
        if (cursor < availableVersions().size() - 1) cursor++;
    }

    List<Version> availableVersions() {
        var focused = focusedUpdateSupplier.get();
        if (focused == null) return List.of();
        return focused.availableVersions().reversed().stream()
                .filter(v -> v.isReleased("version picker"))
                .toList();
    }

    /// Confirms the pick and returns the selected version, or null if nothing valid is selected.
    Version confirmAndClose() {
        var versions = availableVersions();
        if (cursor < 0 || cursor >= versions.size()) return null;
        open = false;
        return versions.get(cursor);
    }
}
