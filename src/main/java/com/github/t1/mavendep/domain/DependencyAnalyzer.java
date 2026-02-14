package com.github.t1.mavendep.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.concurrent.StructuredTaskScope.Subtask;

/// Service for analyzing Maven project dependencies, plugins, and identifying available updates.
///
/// Parses POM files, checks Maven Central for newer versions, and generates reports
/// showing which dependencies and plugins are outdated. Uses structured concurrency with
/// virtual threads for efficient parallel processing when analyzing multiple projects.
/// Automatically discovers and processes modules in multi-module Maven projects.
public class DependencyAnalyzer {

    private final MavenRepository repository;

    public DependencyAnalyzer(MavenRepository repository) {
        this.repository = repository;
    }

    public List<ProjectReport> analyze(List<Path> pomFiles) {
        var allPoms = pomsAndModules(pomFiles).toList();
        resolveParentProperties(allPoms);
        try (var scope = StructuredTaskScope.<ProjectReport>open()) {
            var tasks = allPoms.stream()
                    .map(pom -> scope.fork(() -> analyze(pom)))
                    .toList();
            scope.join();
            return tasks.stream()
                    .map(Subtask::get)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Analysis was interrupted", e);
        }
    }

    private void resolveParentProperties(List<Pom> allPoms) {
        var pomByPath = allPoms.stream()
                .collect(Collectors.toMap(Pom::path, Function.identity()));

        for (var pom : allPoms) {
            if (pom.parent().isEmpty()) continue;
            var pomDir = getParentDir(pom.path());
            var parentDir = pomDir.getParent();
            if (parentDir == null) continue;
            var parentPom = pomByPath.get(parentDir.resolve("pom.xml"));
            if (parentPom != null) {
                pom.resolveUnresolvedVersionsFrom(parentPom);
            }
        }
    }

    private Stream<Pom> pomsAndModules(List<Path> pomFiles) {
        return pomFiles.stream().mapMulti((pomFile, collect) -> Pom.parse(pomFile)
                .ifPresent(pom -> pomAndModules(pom, collect)));
    }

    private void pomAndModules(Pom pom, Consumer<Pom> collect) {
        collect.accept(pom);
        if (!pom.modules().isEmpty()) {
            var parentDir = getParentDir(pom.path());
            pom.modules().forEach(module -> {
                var modulePomFile = parentDir.resolve(module).resolve("pom.xml");
                pomsAndModules(List.of(modulePomFile)).forEach(collect);
            });
        }
    }

    private static Path getParentDir(Path pomFile) {
        var parentDir = pomFile.getParent();
        if (parentDir == null) {
            parentDir = Path.of(".");
        }
        return parentDir;
    }

    private ProjectReport analyze(Pom pom) {
        try (var scope = StructuredTaskScope.<DependencyUpdate>open()) {
            var dependencyUpdatesTasks = submitAnalysis(scope, pom.dependencies());
            var pluginUpdateTasks = submitAnalysis(scope, pom.plugins());
            var parentUpdateTask = submitAnalysis(scope, pom.parent().stream().toList());

            scope.join();

            var dependencyUpdates = await(dependencyUpdatesTasks);
            var pluginUpdates = await(pluginUpdateTasks);
            var parentUpdate = await(parentUpdateTask).stream().findAny();

            var totalDependencies = pom.dependencies().size() + pom.plugins().size() + (pom.parent().isPresent() ? 1 : 0);

            return new ProjectReport(pom, parentUpdate, dependencyUpdates, pluginUpdates, totalDependencies);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Analysis was interrupted", e);
        }
    }

    private List<Subtask<DependencyUpdate>> submitAnalysis(
            StructuredTaskScope<DependencyUpdate, Void> scope,
            List<Dependency> dependencies) {
        return dependencies.stream()
                .map(dependency -> scope.fork(() -> analyze(dependency)))
                .toList();
    }

    private static List<DependencyUpdate> await(List<Subtask<DependencyUpdate>> tasks) {
        return tasks.stream()
                .map(Subtask::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private DependencyUpdate analyze(Dependency dependency) {
        var availableVersions = (dependency.groupId() == null || dependency.artifactId() == null) ? List.<Version>of()
                : repository.getAvailableVersions(dependency.groupId(), dependency.artifactId());
        var context = "scanning available versions of " + dependency.groupId() + ":" + dependency.artifactId();
        var releasedVersions = availableVersions.stream()
                .filter(version -> version.isReleased(context))
                .toList();
        var latestVersion = (releasedVersions.isEmpty()) ? null : releasedVersions.getLast();
        var updateType = UpdateType.between(dependency.version(), latestVersion);
        return dependency.toUpdate(latestVersion, availableVersions, updateType);
    }
}
