package com.github.t1.mavendep.tui.action;

import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.tui.DashboardModel;
import com.github.t1.mavendep.tui.DashboardModel.Phase;

/// Applies selected updates via [Pom#apply] and writes modified POMs to disk.
public class ApplyUpdatesAction {

    private final DashboardModel model;

    public ApplyUpdatesAction(DashboardModel model) {
        this.model = model;
    }

    public void run() {
        model.setPhase(Phase.APPLYING);

        var selectedUpdates = model.selectedUpdates().toList();
        model.reports().stream()
                .filter(ProjectReport::hasUpdates)
                .forEach(report -> report.pom().apply(
                        selectedUpdates.stream()
                                .filter(u -> report.updates().anyMatch(ru ->
                                        ru.groupId().equals(u.groupId()) && ru.artifactId().equals(u.artifactId())))));

        model.reports().stream()
                .map(ProjectReport::pom)
                .filter(Pom::isDirty)
                .forEach(Pom::writeToDisk);

        model.setPhase(Phase.READY);
    }
}
