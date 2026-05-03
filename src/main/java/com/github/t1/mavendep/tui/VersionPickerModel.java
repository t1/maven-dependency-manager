package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.AvailableVersion;
import com.github.t1.mavendep.domain.Update;
import com.github.t1.mavendep.domain.Version;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/// Manages the version picker overlay state within the TUI dashboard.
class VersionPickerModel {
    record Entry(Version version, List<String> sources) {
        String label() {
            return sources.isEmpty() ? version.toString() : version + " [" + String.join(", ", sources) + "]";
        }
    }


    private final Supplier<Update> focusedUpdateSupplier;

    private boolean open;
    private int cursor;

    VersionPickerModel(Supplier<Update> focusedUpdateSupplier) {
        this.focusedUpdateSupplier = focusedUpdateSupplier;
    }

    boolean isOpen() {return open;}

    void open() {
        var focused = focusedUpdateSupplier.get();
        if (focused == null) return;
        open = true;
        cursor = indexOfCurrentVersion(focused.currentVersion());
    }

    void close() {open = false;}

    int cursor() {return cursor;}

    void cursorUp() {
        if (cursor > 0) cursor--;
    }

    void cursorDown() {
        if (cursor < entries().size() - 1) cursor++;
    }

    List<Version> availableVersions() {
        return entries().stream().map(Entry::version).toList();
    }

    List<Entry> entries() {
        var focused = focusedUpdateSupplier.get();
        if (focused == null) return List.of();
        var byVersion = new LinkedHashMap<Version, LinkedHashSet<String>>();
        focused.pickableVersions().forEach(candidate -> merge(byVersion, candidate));
        merge(byVersion, new AvailableVersion(focused.currentVersion(), List.of("current")));
        if (focused.committedVersion() != null) merge(byVersion, new AvailableVersion(focused.committedVersion(), List.of("committed")));
        return byVersion.entrySet().stream()
                .sorted((left, right) -> right.getKey().compareTo(left.getKey()))
                .map(entry -> new Entry(entry.getKey(), entry.getValue().stream().toList()))
                .toList();
    }

    /// Confirms the pick and returns the selected version, or null if nothing valid is selected.
    Version confirmAndClose() {
        var entries = entries();
        if (cursor < 0 || cursor >= entries.size()) return null;
        open = false;
        return entries.get(cursor).version();
    }

    private int indexOfCurrentVersion(Version currentVersion) {
        var entries = entries();
        for (int i = 0; i < entries.size(); i++)
            if (entries.get(i).version().equals(currentVersion)) return i;
        return 0;
    }

    private static void merge(LinkedHashMap<Version, LinkedHashSet<String>> byVersion, AvailableVersion candidate) {
        if (candidate.version() == null) return;
        byVersion.computeIfAbsent(candidate.version(), _ -> new LinkedHashSet<>()).addAll(candidate.sources());
    }
}
