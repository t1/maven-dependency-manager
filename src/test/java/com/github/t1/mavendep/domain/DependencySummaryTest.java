package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static com.github.t1.mavendep.domain.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.minor;
import static com.github.t1.mavendep.domain.UpdateType.none;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static org.assertj.core.api.BDDAssertions.then;

class DependencySummaryTest {

    final Path pomFile = Path.of("pom.xml");
    final Pom pom = Pom.parse(pomFile).orElseThrow();

    @Test
    void shouldCalculateSummary_whenSingleReportWithUpdates() {
        var majorUpdate = createUpdate("com.example", "lib1", "1.0.0", "2.0.0", major);
        var minorUpdate = createUpdate("com.example", "lib2", "1.0.0", "1.1.0", minor);
        var report = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(majorUpdate, minorUpdate),
                List.of(),
                5);

        var summary = DependencySummary.from(report);

        then(summary.totalDependencies()).isEqualTo(5);
        then(summary.outdatedDependencies()).isEqualTo(2);
        then(summary.majorUpdates()).isEqualTo(1);
        then(summary.minorUpdates()).isEqualTo(1);
        then(summary.patchUpdates()).isEqualTo(0);
    }

    @Test
    void shouldCalculateSummary_whenSingleReportWithParentUpdate() {
        var parentUpdate = createUpdate("org.parent", "parent", "1.0.0", "2.0.0", major);
        var minorUpdate = createUpdate("com.example", "lib", "1.0.0", "1.1.0", minor);
        var report = new ProjectReport(
                pom,
                Optional.of(parentUpdate),
                List.of(minorUpdate),
                List.of(),
                3);

        var summary = DependencySummary.from(report);

        then(summary.totalDependencies()).isEqualTo(3);
        then(summary.outdatedDependencies()).isEqualTo(2);
        then(summary.majorUpdates()).isEqualTo(1);
        then(summary.minorUpdates()).isEqualTo(1);
        then(summary.patchUpdates()).isEqualTo(0);
    }

    @Test
    void shouldCalculateSummary_whenSingleReportWithPluginUpdates() {
        var pluginUpdate = createUpdate("org.apache.maven.plugins", "maven-compiler-plugin", "3.0.0", "3.1.0", minor);
        var dependencyUpdate = createUpdate("com.example", "lib", "1.0.0", "1.0.1", patch);
        var report = new ProjectReport(pom, Optional.empty(), List.of(dependencyUpdate), List.of(pluginUpdate), 4);

        var summary = DependencySummary.from(report);

        then(summary.totalDependencies()).isEqualTo(4);
        then(summary.outdatedDependencies()).isEqualTo(2);
        then(summary.majorUpdates()).isEqualTo(0);
        then(summary.minorUpdates()).isEqualTo(1);
        then(summary.patchUpdates()).isEqualTo(1);
    }

    @Test
    void shouldCalculateSummary_whenSingleReportWithNoUpdates() {
        var report = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(),
                List.of(),
                3);

        var summary = DependencySummary.from(report);

        then(summary.totalDependencies()).isEqualTo(3);
        then(summary.outdatedDependencies()).isEqualTo(0);
        then(summary.majorUpdates()).isEqualTo(0);
        then(summary.minorUpdates()).isEqualTo(0);
        then(summary.patchUpdates()).isEqualTo(0);
    }

    @Test
    void shouldNotCountCurrentDependencies_whenVersionsAreEqual() {
        var noUpdate = createUpdate("com.example", "lib", "1.0.0", "1.0.0", none);
        var report = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(noUpdate),
                List.of(),
                5);

        var summary = DependencySummary.from(report);

        then(summary.outdatedDependencies()).isEqualTo(0);
        then(summary.majorUpdates()).isEqualTo(0);
        then(summary.minorUpdates()).isEqualTo(0);
        then(summary.patchUpdates()).isEqualTo(0);
    }

    @Test
    void shouldCalculateSummary_whenMultipleReports() {
        var majorUpdate1 = createUpdate("com.example", "lib1", "1.0.0", "2.0.0", major);
        var minorUpdate1 = createUpdate("com.example", "lib2", "1.0.0", "1.1.0", minor);
        var report1 = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(majorUpdate1, minorUpdate1),
                List.of(),
                5);
        var patchUpdate2 = createUpdate("org.test", "lib3", "2.0.0", "2.0.1", patch);
        var report2 = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(patchUpdate2),
                List.of(),
                3);

        var summary = DependencySummary.from(List.of(report1, report2));

        then(summary.totalDependencies()).isEqualTo(8);
        then(summary.outdatedDependencies()).isEqualTo(3);
        then(summary.majorUpdates()).isEqualTo(1);
        then(summary.minorUpdates()).isEqualTo(1);
        then(summary.patchUpdates()).isEqualTo(1);
    }

    @Test
    void shouldCalculateSummary_whenMultipleReportsWithParents() {
        var parentUpdate1 = createUpdate("org.parent", "parent1", "1.0.0", "2.0.0", major);
        var update1 = createUpdate("com.example", "lib1", "1.0.0", "1.1.0", minor);
        var report1 = new ProjectReport(
                pom,
                Optional.of(parentUpdate1),
                List.of(update1),
                List.of(),
                4);
        var parentUpdate2 = createUpdate("org.parent", "parent2", "1.0.0", "1.0.1", patch);
        var update2 = createUpdate("com.example", "lib2", "2.0.0", "3.0.0", major);
        var report2 = new ProjectReport(
                pom,
                Optional.of(parentUpdate2),
                List.of(update2),
                List.of(),
                6);

        var summary = DependencySummary.from(List.of(report1, report2));

        then(summary.totalDependencies()).isEqualTo(10);
        then(summary.outdatedDependencies()).isEqualTo(4);
        then(summary.majorUpdates()).isEqualTo(2);
        then(summary.minorUpdates()).isEqualTo(1);
        then(summary.patchUpdates()).isEqualTo(1);
    }

    @Test
    void shouldCalculateSummary_whenEmptyReportList() {
        var summary = DependencySummary.from(List.of());

        then(summary.totalDependencies()).isEqualTo(0);
        then(summary.outdatedDependencies()).isEqualTo(0);
        then(summary.majorUpdates()).isEqualTo(0);
        then(summary.minorUpdates()).isEqualTo(0);
        then(summary.patchUpdates()).isEqualTo(0);
    }

    @Test
    void shouldCountPatchUpdates_whenOnlyPatchesAvailable() {
        var patch1 = createUpdate("com.example", "lib1", "1.0.0", "1.0.1", patch);
        var patch2 = createUpdate("com.example", "lib2", "2.1.3", "2.1.4", patch);
        var report = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(patch1, patch2),
                List.of(),
                5);

        var summary = DependencySummary.from(report);

        then(summary.outdatedDependencies()).isEqualTo(2);
        then(summary.majorUpdates()).isEqualTo(0);
        then(summary.minorUpdates()).isEqualTo(0);
        then(summary.patchUpdates()).isEqualTo(2);
    }

    @Test
    void shouldNotCountNullVersions_whenCurrentVersionIsNull() {
        var updateWithNullCurrent = createUpdateWithNullCurrent();
        var report = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(updateWithNullCurrent),
                List.of(),
                2);

        var summary = DependencySummary.from(report);

        then(summary.outdatedDependencies()).isEqualTo(0);
    }

    @Test
    void shouldNotCountNullVersions_whenLatestVersionIsNull() {
        var updateWithNullLatest = createUpdateWithNullLatest();
        var report = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(updateWithNullLatest),
                List.of(),
                2);

        var summary = DependencySummary.from(report);

        then(summary.outdatedDependencies()).isEqualTo(0);
    }

    @Test
    void shouldCountAllUpdateTypes_whenMixedUpdatesPresent() {
        var majorUpdate = createUpdate("com.example", "lib1", "1.0.0", "2.0.0", major);
        var minorUpdate = createUpdate("com.example", "lib2", "1.0.0", "1.1.0", minor);
        var patchUpdate = createUpdate("com.example", "lib3", "1.0.0", "1.0.1", patch);
        var report = new ProjectReport(
                pom,
                Optional.empty(),
                List.of(majorUpdate, minorUpdate, patchUpdate),
                List.of(),
                10);

        var summary = DependencySummary.from(report);

        then(summary.outdatedDependencies()).isEqualTo(3);
        then(summary.majorUpdates()).isEqualTo(1);
        then(summary.minorUpdates()).isEqualTo(1);
        then(summary.patchUpdates()).isEqualTo(1);
    }

    private DependencyUpdate createUpdate(
            String groupId,
            String artifactId,
            String currentVersion,
            String latestVersion,
            UpdateType updateType) {
        return new DependencyUpdate(
                dependency,
                groupId,
                artifactId,
                Version.fromString(currentVersion),
                compile,
                null,
                Version.fromString(latestVersion),
                List.of(Version.fromString(currentVersion), Version.fromString(latestVersion)),
                updateType
        );
    }

    private DependencyUpdate createUpdateWithNullCurrent() {
        return new DependencyUpdate(
                dependency,
                "com.example",
                "lib",
                null,
                compile,
                null,
                Version.fromString("1.0.0"),
                List.of(Version.fromString("1.0.0")),
                major
        );
    }

    private DependencyUpdate createUpdateWithNullLatest() {
        return new DependencyUpdate(
                dependency,
                "com.example",
                "lib",
                Version.fromString("1.0.0"),
                compile,
                null,
                null,
                List.of(Version.fromString("1.0.0")),
                none
        );
    }
}
