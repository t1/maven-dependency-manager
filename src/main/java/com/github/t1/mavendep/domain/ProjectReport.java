package com.github.t1.mavendep.domain;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ProjectReport(
        Pom pom,
        Optional<DependencyUpdate> parentUpdate,
        List<DependencyUpdate> dependencyUpdates,
        List<DependencyUpdate> pluginUpdates,
        int totalDependencies) {

    public ProjectReport {
        requireNonNull(pom);
        requireNonNull(parentUpdate);
        requireNonNull(dependencyUpdates);
        requireNonNull(pluginUpdates);
        if (totalDependencies < 0) throw new IllegalArgumentException();
    }
}
