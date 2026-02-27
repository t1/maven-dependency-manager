package com.github.t1.mavendep.domain;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

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

    public Stream<DependencyUpdate> updates() {
        return Stream.concat(parentUpdate.stream(),
                Stream.concat(
                                dependencyUpdates.stream(),
                                pluginUpdates.stream())
                        .filter(DependencyUpdate::isUpdatable));
    }

    public boolean hasUpdates() {
        return updates().findAny().isPresent();
    }

    public ProjectReport filterUpdates(Predicate<DependencyUpdate> predicate) {
        return new ProjectReport(
                pom,
                parentUpdate.filter(predicate),
                dependencyUpdates.stream().filter(predicate).toList(),
                pluginUpdates.stream().filter(predicate).toList(),
                totalDependencies);
    }

    public ProjectReport onlyUpdates() {
        return filterUpdates(DependencyUpdate::isUpdatable);
    }
}
