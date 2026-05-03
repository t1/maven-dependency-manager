package com.github.t1.mavendep.domain;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public record ProjectReport(
        Pom pom,
        Optional<Update> parentUpdate,
        List<Update> dependencyUpdates,
        List<Update> pluginUpdates,
        int totalDependencies) {

    public ProjectReport {
        requireNonNull(pom);
        requireNonNull(parentUpdate);
        requireNonNull(dependencyUpdates);
        requireNonNull(pluginUpdates);
        if (totalDependencies < 0) throw new IllegalArgumentException();
    }

    public Stream<Update> updates() {
        return Stream.concat(parentUpdate.stream(),
                Stream.concat(
                                dependencyUpdates.stream(),
                                pluginUpdates.stream())
                        .filter(Update::isUpdateAvailable));
    }

    public boolean hasUpdates() {
        return updates().findAny().isPresent();
    }

    public ProjectReport filterUpdates(Predicate<Update> predicate) {
        return new ProjectReport(
                pom,
                parentUpdate.filter(predicate),
                dependencyUpdates.stream().filter(predicate).toList(),
                pluginUpdates.stream().filter(predicate).toList(),
                totalDependencies);
    }

    public ProjectReport onlyUpdates() {
        return filterUpdates(Update::isUpdateAvailable);
    }
}
