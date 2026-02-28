package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;

/// Applies selected updates via [Pom#apply] and writes modified POMs to disk.
class ApplyUpdatesAction {

    private final DashboardModel model;

    ApplyUpdatesAction(DashboardModel model) {
        this.model = model;
    }

    void run() {
        // Reset all POMs to original state before re-applying current selection
        model.reports().stream()
                .map(ProjectReport::pom)
                .forEach(Pom::reset);

        var selectedUpdates = model.selectedUpdates().toList();
        model.reports().stream()
                .filter(ProjectReport::hasUpdates)
                .forEach(report -> report.pom().apply(
                        selectedUpdates.stream()
                                .filter(u -> report.updates().anyMatch(ru ->
                                        ru.groupId().equals(u.groupId()) && ru.artifactId().equals(u.artifactId())))));

        model.reports().stream()
                .map(ProjectReport::pom)
                .forEach(Pom::writeToDisk);
    }
}
