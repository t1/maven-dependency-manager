package com.github.t1.mavendep.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.DependencyType.dependency;
import static com.github.t1.mavendep.domain.DependencyType.parent;
import static com.github.t1.mavendep.domain.DependencyType.plugin;
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
        try (var scope = StructuredTaskScope.<ProjectReport>open()) {
            var tasks = pomsAndModules(pomFiles)
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
            var dependencyUpdatesTasks = submitAnalysis(scope, pom.dependencies(), dependency);
            var pluginUpdateTasks = submitAnalysis(scope, pom.plugins(), plugin);
            var parentUpdateTask = submitAnalysis(scope, pom.parent().stream().toList(), parent);

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
            List<Dependency> dependencies,
            DependencyType dependencyType) {
        return dependencies.stream()
                .map(dep -> scope.fork(() -> analyze(dep, dependencyType)))
                .toList();
    }

    private static List<DependencyUpdate> await(List<Subtask<DependencyUpdate>> tasks) {
        return tasks.stream()
                .map(Subtask::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private DependencyUpdate analyze(Dependency dependency, DependencyType dependencyType) {
        var availableVersions = (dependency.groupId() == null || dependency.artifactId() == null) ? List.<Version>of()
                : repository.getAvailableVersions(dependency.groupId(), dependency.artifactId());
        var releasedVersions = availableVersions.stream().filter(Version::isReleased).toList();
        var latestVersion = (releasedVersions.isEmpty()) ? null : releasedVersions.getLast();
        var updateType = UpdateType.between(dependency.version(), latestVersion);
        return dependency.toUpdate(dependencyType, latestVersion, availableVersions, updateType);
    }
}
