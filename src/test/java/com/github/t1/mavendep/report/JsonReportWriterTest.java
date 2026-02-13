package com.github.t1.mavendep.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.mavendep.domain.Dependency;
import com.github.t1.mavendep.domain.DependencyUpdate;
import com.github.t1.mavendep.domain.Pom;
import com.github.t1.mavendep.domain.ProjectReport;
import com.github.t1.mavendep.domain.Version;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.parent;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.minor;
import static com.github.t1.mavendep.domain.UpdateType.none;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static org.assertj.core.api.BDDAssertions.then;

class JsonReportWriterTest {
    private static final Pom DUMMY_POM = Pom.parse(Path.of("pom.xml")).orElseThrow();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldGenerateJsonForSingleUpdate() throws IOException {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(Version.fromString("5.10.0"), Version.fromString("5.10.1")),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var writer = new JsonReportWriter();
        var json = writer.write(List.of(report.onlyUpdates()));

        var root = objectMapper.readTree(json);
        then(root.get("projects")).hasSize(1);

        var project = root.get("projects").get(0);
        then(project.get("pomFile").asText()).isEqualTo(DUMMY_POM.path().toAbsolutePath().toString());
        then(project.get("dependencies")).hasSize(1);

        var dep = project.get("dependencies").get(0);
        then(dep.get("groupId").asText()).isEqualTo("org.junit.jupiter");
        then(dep.get("artifactId").asText()).isEqualTo("junit-jupiter");
        then(dep.get("currentVersion").asText()).isEqualTo("5.10.0");
        then(dep.get("latestVersion").asText()).isEqualTo("5.10.1");
        then(dep.get("updateType").asText()).isEqualTo("patch");
    }

    @Test
    void shouldGenerateSummary() throws IOException {
        var update1 = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var update2 = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("5.3.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                major
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update1, update2), List.of(), 2);

        var writer = new JsonReportWriter();
        var json = writer.write(List.of(report.onlyUpdates()));

        var root = objectMapper.readTree(json);
        then(root.get("summary")).isNotNull();
        then(root.get("summary").get("totalDependencies").asInt()).isEqualTo(2);
        then(root.get("summary").get("outdatedDependencies").asInt()).isEqualTo(2);
        then(root.get("summary").get("majorUpdates").asInt()).isEqualTo(1);
        then(root.get("summary").get("minorUpdates").asInt()).isEqualTo(0);
        then(root.get("summary").get("patchUpdates").asInt()).isEqualTo(1);
    }

    @Test
    void shouldIncludeSummaryInOutput() throws IOException {
        var outdated = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var upToDate = new DependencyUpdate(
                new Dependency(dependency, "org.springframework", "spring-core", Version.fromString("6.0.0"), compile, null),
                Version.fromString("6.0.0"),
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(outdated, upToDate), List.of(), 2);

        var writer = new JsonReportWriter();
        var json = writer.write(List.of(report.onlyUpdates()));

        var root = objectMapper.readTree(json);
        then(root.get("summary")).isNotNull();
        then(root.get("summary").get("totalDependencies").asInt()).isEqualTo(2);
        then(root.get("summary").get("outdatedDependencies").asInt()).isEqualTo(1);
    }

    @Test
    void shouldIncludeScopeInJsonOutput() throws IOException {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), test, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var writer = new JsonReportWriter();
        var json = writer.write(List.of(report.onlyUpdates()));

        var root = objectMapper.readTree(json);
        var dep = root.get("projects").get(0).get("dependencies").get(0);
        then(dep.get("scope").asText()).isEqualTo("test");
    }

    @Test
    void shouldOutputNullWhenLatestVersionIsNull() throws IOException {
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "unknown-artifact", Version.fromString("1.0.0"), compile, null),
                null,
                List.of(),
                none
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var writer = new JsonReportWriter();
        var json = writer.write(List.of(report));

        var root = objectMapper.readTree(json);
        var dep = root.get("projects").get(0).get("dependencies").get(0);
        then(dep.get("groupId").asText()).isEqualTo("com.example");
        then(dep.get("artifactId").asText()).isEqualTo("unknown-artifact");
        then(dep.get("currentVersion").asText()).isEqualTo("1.0.0");
        then(dep.get("latestVersion").isNull()).isTrue();
    }

    @Test
    void shouldIncludeTypeFieldWithDependencyValue() throws IOException {
        var update = new DependencyUpdate(
                new Dependency(dependency, "org.junit.jupiter", "junit-jupiter", Version.fromString("5.10.0"), compile, null),
                Version.fromString("5.10.1"),
                List.of(),
                patch
        );
        var report = new ProjectReport(DUMMY_POM, Optional.empty(), List.of(update), List.of(), 1);

        var writer = new JsonReportWriter();
        var json = writer.write(List.of(report.onlyUpdates()));

        var root = objectMapper.readTree(json);
        var dep = root.get("projects").get(0).get("dependencies").get(0);
        then(dep.get("type").asText()).isEqualTo("dependency");
    }

    @Test
    void shouldIncludeTypeFieldWithParentValueAndEmptyScope() throws IOException {
        var parentUpdate = new DependencyUpdate(
                new Dependency(parent, "org.springframework.boot", "spring-boot-starter-parent", Version.fromString("3.1.0"), compile, null),
                Version.fromString("3.2.0"),
                List.of(),
                minor
        );
        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), List.of(), List.of(), 0);

        var writer = new JsonReportWriter();
        var json = writer.write(List.of(report.onlyUpdates()));

        var root = objectMapper.readTree(json);
        var parent = root.get("projects").get(0).get("parent");
        then(parent.get("type").asText()).isEqualTo("parent");
        then(parent.get("scope").asText()).isEmpty();
    }
}
