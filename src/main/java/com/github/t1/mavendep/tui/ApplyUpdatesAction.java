package com.github.t1.mavendep.tui;

import com.github.t1.mavendep.domain.Pom;

/// Applies selected updates via [Pom#apply] and writes modified POMs to disk.
class ApplyUpdatesAction {

    private final DashboardModel model;

    ApplyUpdatesAction(DashboardModel model) {
        this.model = model;
    }

    void run() {
        // Reset all POMs to original state before re-applying current selection
        model.reports().stream()
                .map(com.github.t1.mavendep.domain.ProjectReport::pom)
                .forEach(Pom::reset);

        model.reports().forEach(report -> report.pom().apply(model.selectedUpdatesOf(report)));

        model.reports().stream()
                .map(com.github.t1.mavendep.domain.ProjectReport::pom)
                .forEach(Pom::writeToDisk);
    }
}
