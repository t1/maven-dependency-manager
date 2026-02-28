package com.github.t1.mavendep.tui.action;

import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.tui.DashboardModel;

/// Applies selected updates via [Pom#apply] and writes modified POMs to disk.
public class ApplyUpdatesAction {

    private final DashboardModel model;

    public ApplyUpdatesAction(DashboardModel model) {
        this.model = model;
    }

    public void run() {
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
