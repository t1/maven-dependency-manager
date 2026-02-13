package com.github.t1.mavendep.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.Dependency.DependencyType;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Scope;
import com.github.t1.mavendep.domain.UpdateType;
import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.parent;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.minor;
import static com.github.t1.mavendep.domain.UpdateType.none;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.BDDAssertions.then;

class JsonReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateJsonForSingleUpdate() {
        var update = createUpdate("org.junit.jupiter", "junit-jupiter", "5.10.0", "5.10.1", patch);
        var report = new ProjectReport(pom(), Optional.empty(), List.of(update), List.of(), 1);

        var root = write(report.onlyUpdates());

        then(root.get("projects")).hasSize(1);
        var project = root.get("projects").get(0);
        then(project.get("pomFile").asText()).isEqualTo(pom().path().toAbsolutePath().toString());
        then(project.get("dependencies")).hasSize(1);
        var dep = project.get("dependencies").get(0);
        then(dep.get("groupId").asText()).isEqualTo("org.junit.jupiter");
        then(dep.get("artifactId").asText()).isEqualTo("junit-jupiter");
        then(dep.get("currentVersion").asText()).isEqualTo("5.10.0");
        then(dep.get("latestVersion").asText()).isEqualTo("5.10.1");
        then(dep.get("updateType").asText()).isEqualTo("patch");
    }

    @Test
    void shouldGenerateSummary() {
        var update1 = createUpdate("org.junit.jupiter", "junit-jupiter", "5.10.0", "5.10.1", patch);
        var update2 = createUpdate("org.springframework", "spring-core", "5.3.0", "6.0.0", major);
        var report = new ProjectReport(pom(), Optional.empty(), List.of(update1, update2), List.of(), 2);

        var root = write(report.onlyUpdates());

        then(root.get("summary")).isNotNull();
        then(root.get("summary").get("totalDependencies").asInt()).isEqualTo(2);
        then(root.get("summary").get("outdatedDependencies").asInt()).isEqualTo(2);
        then(root.get("summary").get("majorUpdates").asInt()).isEqualTo(1);
        then(root.get("summary").get("minorUpdates").asInt()).isEqualTo(0);
        then(root.get("summary").get("patchUpdates").asInt()).isEqualTo(1);
    }

    @Test
    void shouldExcludeUpToDateFromOutdatedCount() {
        var outdated = createUpdate("org.junit.jupiter", "junit-jupiter", "5.10.0", "5.10.1", patch);
        var upToDate = createUpdate("org.springframework", "spring-core", "6.0.0", "6.0.0", none);
        var report = new ProjectReport(pom(), Optional.empty(), List.of(outdated, upToDate), List.of(), 2);

        var root = write(report.onlyUpdates());

        then(root.get("summary")).isNotNull();
        then(root.get("summary").get("totalDependencies").asInt()).isEqualTo(2);
        then(root.get("summary").get("outdatedDependencies").asInt()).isEqualTo(1);
    }

    @Test
    void shouldIncludeScopeInJsonOutput() {
        var update = createUpdate(dependency, "org.junit.jupiter", "junit-jupiter", "5.10.0", "5.10.1", test, patch);
        var report = new ProjectReport(pom(), Optional.empty(), List.of(update), List.of(), 1);

        var root = write(report.onlyUpdates());

        var dep = root.get("projects").get(0).get("dependencies").get(0);
        then(dep.get("scope").asText()).isEqualTo("test");
    }

    @Test
    void shouldOutputNullLatestVersionInUnfilteredReport() {
        var update = createUpdateWithNullLatest();
        var report = new ProjectReport(pom(), Optional.empty(), List.of(update), List.of(), 1);

        var root = write(report);

        var dep = root.get("projects").get(0).get("dependencies").get(0);
        then(dep.get("groupId").asText()).isEqualTo("com.example");
        then(dep.get("artifactId").asText()).isEqualTo("unknown-artifact");
        then(dep.get("currentVersion").asText()).isEqualTo("1.0.0");
        then(dep.get("latestVersion").isNull()).isTrue();
    }

    @Test
    void shouldIncludeTypeFieldWithDependencyValue() {
        var update = createUpdate("org.junit.jupiter", "junit-jupiter", "5.10.0", "5.10.1", patch);
        var report = new ProjectReport(pom(), Optional.empty(), List.of(update), List.of(), 1);

        var root = write(report.onlyUpdates());

        var dep = root.get("projects").get(0).get("dependencies").get(0);
        then(dep.get("type").asText()).isEqualTo("dependency");
    }

    @Test
    void shouldIncludeTypeFieldWithParentValueAndEmptyScope() {
        var parentUpdate = createUpdate(parent, "org.springframework.boot", "spring-boot-starter-parent", "3.1.0", "3.2.0", compile, minor);
        var report = new ProjectReport(pom(), Optional.of(parentUpdate), List.of(), List.of(), 0);

        var root = write(report.onlyUpdates());

        var parentNode = root.get("projects").get(0).get("parent");
        then(parentNode.get("type").asText()).isEqualTo("parent");
        then(parentNode.get("scope").asText()).isEmpty();
    }

    @Test
    void shouldIncludePluginsInJsonOutput() {
        var pluginUpdate = createUpdate(plugin, "org.apache.maven.plugins", "maven-compiler-plugin", "3.11.0", "3.12.0", compile, patch);
        var report = new ProjectReport(pom(), Optional.empty(), List.of(), List.of(pluginUpdate), 1);

        var root = write(report.onlyUpdates());

        var plugins = root.get("projects").get(0).get("plugins");
        then(plugins).hasSize(1);
        var pluginNode = plugins.get(0);
        then(pluginNode.get("type").asText()).isEqualTo("plugin");
        then(pluginNode.get("groupId").asText()).isEqualTo("org.apache.maven.plugins");
        then(pluginNode.get("artifactId").asText()).isEqualTo("maven-compiler-plugin");
        then(pluginNode.get("currentVersion").asText()).isEqualTo("3.11.0");
        then(pluginNode.get("latestVersion").asText()).isEqualTo("3.12.0");
        then(pluginNode.get("scope").asText()).isEmpty();
    }

    @Test
    void shouldGenerateJsonForMultipleProjects() {
        var update1 = createUpdate("com.example", "lib1", "1.0.0", "2.0.0", major);
        var report1 = new ProjectReport(pom(), Optional.empty(), List.of(update1), List.of(), 3);
        var update2 = createUpdate("com.example", "lib2", "1.0.0", "1.0.1", patch);
        var report2 = new ProjectReport(pom(), Optional.empty(), List.of(update2), List.of(), 2);

        var root = write(report1.onlyUpdates(), report2.onlyUpdates());

        then(root.get("projects")).hasSize(2);
        then(root.get("summary").get("totalDependencies").asInt()).isEqualTo(5);
        then(root.get("summary").get("outdatedDependencies").asInt()).isEqualTo(2);
        then(root.get("summary").get("majorUpdates").asInt()).isEqualTo(1);
        then(root.get("summary").get("patchUpdates").asInt()).isEqualTo(1);
    }

    private Pom pom() {
        var pomFile = tempDir.resolve("pom.xml");
        if (!pomFile.toFile().exists()) {
            try {
                writeString(pomFile, """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>test-project</artifactId>
                            <version>1.0.0</version>
                        </project>
                        """);
            } catch (IOException e) {
                throw new RuntimeException("failed to write " + pomFile, e);
            }
        }
        return Pom.parse(pomFile).orElseThrow();
    }

    private JsonNode write(ProjectReport... reports) {
        var json = new JsonReportWriter().write(List.of(reports));
        try {
            return new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("failed to parse JSON", e);
        }
    }

    private DependencyUpdate createUpdate(String groupId, String artifactId, String currentVersion, String latestVersion, UpdateType updateType) {
        return createUpdate(dependency, groupId, artifactId, currentVersion, latestVersion, compile, updateType);
    }

    private DependencyUpdate createUpdate(DependencyType type, String groupId, String artifactId, String currentVersion, String latestVersion, Scope scope, UpdateType updateType) {
        return new DependencyUpdate(
                new Dependency(type, groupId, artifactId, Version.fromString(currentVersion), scope, null),
                Version.fromString(latestVersion),
                List.of(Version.fromString(currentVersion), Version.fromString(latestVersion)),
                updateType
        );
    }

    private DependencyUpdate createUpdateWithNullLatest() {
        return new DependencyUpdate(
                new Dependency(dependency, "com.example", "unknown-artifact", Version.fromString("1.0.0"), compile, null),
                null,
                List.of(Version.fromString("1.0.0")),
                none
        );
    }
}
