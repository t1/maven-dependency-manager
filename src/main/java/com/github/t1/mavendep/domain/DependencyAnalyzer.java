package com.github.t1.mavendep.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.UpdateType.none;
import static java.util.concurrent.StructuredTaskScope.Subtask;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

/// Service for analyzing Maven project dependencies, plugins, and identifying available updates.
///
/// Parses POM files, checks Maven Central for newer versions, and generates reports
/// showing which dependencies and plugins are outdated. Uses structured concurrency with
/// virtual threads for efficient parallel processing when analyzing multiple projects.
/// Automatically discovers and processes modules in multi-module Maven projects.
/// Skips metadata fetching for local project artifacts (parents and inter-module dependencies)
/// that are already in the list of analyzed projects.
public class DependencyAnalyzer {
    private static final String POM_FILENAME = "pom.xml";

    private final MavenRepository repository;
    private final List<Path> pomFiles;

    private Set<Coordinates> localArtifacts;

    public DependencyAnalyzer(MavenRepository repository, Path... pomFiles) {
        this(repository, List.of(pomFiles));
    }

    public DependencyAnalyzer(MavenRepository repository, List<Path> pomFiles) {
        this.repository = repository;
        this.pomFiles = pomFiles;
    }

    public List<ProjectReport> run() {
        var resolvedFiles = pomFiles.stream().map(DependencyAnalyzer::resolveToPomFile).toList();
        var allPoms = pomsAndModules(resolvedFiles).toList();
        resolveParentProperties(allPoms);
        localArtifacts = allPoms.stream().map(Pom::coordinates).collect(toSet());
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

    private static Path resolveToPomFile(Path path) {
        return Files.isDirectory(path) ? path.resolve(POM_FILENAME) : path;
    }

    private void resolveParentProperties(List<Pom> allPoms) {
        var pomByPath = allPoms.stream()
                .collect(toMap(Pom::path, v -> v));

        for (var pom : allPoms) {
            if (pom.parent().isEmpty()) continue;
            var pomDir = containingDir(pom.path());
            var parentDir = pomDir.getParent();
            if (parentDir == null) continue;
            var parentPom = pomByPath.get(parentDir.resolve(POM_FILENAME));
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
            var parentDir = containingDir(pom.path());
            pom.modules().forEach(module -> {
                var modulePomFile = parentDir.resolve(module).resolve(POM_FILENAME);
                pomsAndModules(List.of(modulePomFile)).forEach(collect);
            });
        }
    }

    private static Path containingDir(Path pomFile) {
        var parentDir = pomFile.getParent();
        if (parentDir == null) {
            parentDir = Path.of(".");
        }
        return parentDir;
    }

    private ProjectReport analyze(Pom pom) {
        try (var scope = StructuredTaskScope.<DependencyUpdate>open()) {
            var dependencyUpdatesTasks = submitAnalysis(scope, pom.dependencies().stream()
                    .filter(this::isExternalArtifact));
            var pluginUpdateTasks = submitAnalysis(scope, pom.plugins().stream());
            var parentUpdateTask = submitAnalysis(scope, pom.parent().stream()
                    .filter(this::isExternalArtifact));

            scope.join();

            var dependencyUpdates = await(dependencyUpdatesTasks);
            var pluginUpdates = await(pluginUpdateTasks);
            var parentUpdate = await(parentUpdateTask).stream().findAny()
                    .or(() -> localParentUpdate(pom));

            var totalDependencies = pom.dependencies().size() + pom.plugins().size() + (pom.parent().isPresent() ? 1 : 0);

            return new ProjectReport(pom, parentUpdate, dependencyUpdates, pluginUpdates, totalDependencies);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Analysis was interrupted", e);
        }
    }

    private boolean isExternalArtifact(Dependency dep) {
        return !isLocalArtifact(dep);
    }

    private List<Subtask<DependencyUpdate>> submitAnalysis(
            StructuredTaskScope<DependencyUpdate, Void> scope,
            Stream<Dependency> dependencies) {
        return dependencies.map(dependency -> scope.fork(() -> analyze(dependency))).toList();
    }

    private static List<DependencyUpdate> await(List<Subtask<DependencyUpdate>> tasks) {
        return tasks.stream()
                .map(Subtask::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private Optional<DependencyUpdate> localParentUpdate(Pom pom) {
        return pom.parent()
                .filter(this::isLocalArtifact)
                .map(dep -> dep.toUpdate(dep.version(), List.of(), none));
    }

    private boolean isLocalArtifact(Dependency dep) {
        return localArtifacts.contains(dep.coordinates());
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
