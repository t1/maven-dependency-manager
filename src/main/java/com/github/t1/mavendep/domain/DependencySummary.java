package com.github.t1.mavendep.domain;

import java.util.ArrayList;
import java.util.List;

public record DependencySummary(
        int totalDependencies,
        int outdatedDependencies,
        int majorUpdates,
        int minorUpdates,
        int patchUpdates
) {

    /// Calculates summary statistics from a single project report.
    ///
    /// ## Calculation
    /// - **totalDependencies**: Total count from ProjectReport (includes all dependencies, not just shown)
    /// - **outdatedDependencies**: Count of dependencies, parent, and plugins where current != latest version
    /// - **majorUpdates**: Count of dependencies, parent, and plugins with MAJOR update type
    /// - **minorUpdates**: Count of dependencies, parent, and plugins with MINOR update type
    /// - **patchUpdates**: Count of dependencies, parent, and plugins with PATCH update type
    ///
    /// @param report Project report to summarize
    /// @return Summary statistics
    public static DependencySummary from(ProjectReport report) {
        return from(List.of(report));
    }

    /// Calculates summary statistics from a list of project reports.
    ///
    /// ## Calculation
    /// - **totalDependencies**: Sum of total dependencies from all projects
    /// - **outdatedDependencies**: Count of dependencies, parent, and plugins where current != latest version
    /// - **majorUpdates**: Count of dependencies, parent, and plugins with MAJOR update type
    /// - **minorUpdates**: Count of dependencies, parent, and plugins with MINOR update type
    /// - **patchUpdates**: Count of dependencies, parent, and plugins with PATCH update type
    ///
    /// @param reports List of project reports to summarize
    /// @return Summary statistics
    public static DependencySummary from(List<ProjectReport> reports) {
        var totalDependencies = 0;
        var outdatedDependencies = 0;
        var majorUpdates = 0;
        var minorUpdates = 0;
        var patchUpdates = 0;

        for (var report : reports) {
            totalDependencies += report.totalDependencies();
            var allUpdates = getAllUpdates(report);
            for (var update : allUpdates) {
                if (update.isUpdate()) {
                    outdatedDependencies++;
                    switch (update.updateType()) {
                        case major -> majorUpdates++;
                        case minor -> minorUpdates++;
                        case patch -> patchUpdates++;
                    }
                }
            }
        }

        return new DependencySummary(
                totalDependencies,
                outdatedDependencies,
                majorUpdates,
                minorUpdates,
                patchUpdates
        );
    }

    private static List<DependencyUpdate> getAllUpdates(ProjectReport report) {
        var allUpdates = new ArrayList<DependencyUpdate>();
        report.parentUpdate().ifPresent(allUpdates::add);
        allUpdates.addAll(report.dependencyUpdates());
        allUpdates.addAll(report.pluginUpdates());
        return allUpdates;
    }
}
