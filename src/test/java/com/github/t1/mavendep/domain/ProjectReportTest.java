package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.minor;
import static org.assertj.core.api.BDDAssertions.then;

class ProjectReportTest {
    private static final Pom DUMMY_POM = Pom.parse(Path.of("pom.xml")).orElseThrow();

    @Test
    void shouldCreateRecord_whenAllFieldsProvided() {
        var parentUpdate = new DependencyUpdate(
                dependency,
                "org.parent",
                "parent",
                Version.fromString("1.0.0"),
                compile,
                null,
                Version.fromString("2.0.0"),
                List.of(Version.fromString("1.0.0"), Version.fromString("2.0.0")),
                major
        );
        var update = new DependencyUpdate(
                dependency,
                "com.example",
                "lib",
                Version.fromString("1.0.0"),
                compile,
                null,
                Version.fromString("1.1.0"),
                List.of(Version.fromString("1.0.0"), Version.fromString("1.1.0")),
                minor
        );
        var updates = List.of(update);

        var report = new ProjectReport(DUMMY_POM, Optional.of(parentUpdate), updates, List.of(), 5);

        then(report.pom()).isEqualTo(DUMMY_POM);
        then(report.parentUpdate()).contains(parentUpdate);
        then(report.dependencyUpdates()).isEqualTo(updates);
        then(report.totalDependencies()).isEqualTo(5);
    }

    @Test
    void shouldCreateRecordWithNullParent_whenNoParentUpdate() {
        var updates = List.<DependencyUpdate>of();

        var report = new ProjectReport(DUMMY_POM, Optional.empty(), updates, List.of(), 3);

        then(report.pom()).isEqualTo(DUMMY_POM);
        then(report.parentUpdate()).isEmpty();
        then(report.dependencyUpdates()).isEmpty();
        then(report.totalDependencies()).isEqualTo(3);
    }

    @Test
    void shouldCreateRecordWithEmptyUpdates_whenNoUpdatesAvailable() {
        var emptyUpdates = List.<DependencyUpdate>of();

        var report = new ProjectReport(DUMMY_POM, Optional.empty(), emptyUpdates, List.of(), 10);

        then(report.dependencyUpdates()).isEmpty();
        then(report.totalDependencies()).isEqualTo(10);
    }
}
