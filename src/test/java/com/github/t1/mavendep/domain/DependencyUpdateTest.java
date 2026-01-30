package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.t1.mavendep.domain.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.runtime;
import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.minor;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static org.assertj.core.api.BDDAssertions.then;

class DependencyUpdateTest {

    @Test
    void shouldCreateRecord_whenAllFieldsProvided() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("2.0.0");
        var availableVersions = List.of(currentVersion, latestVersion);

        var update = new DependencyUpdate(
                dependency,
                "com.example",
                "lib",
                currentVersion,
                compile,
                latestVersion,
                availableVersions,
                major
        );

        then(update.groupId()).isEqualTo("com.example");
        then(update.artifactId()).isEqualTo("lib");
        then(update.currentVersion()).isEqualTo(currentVersion);
        then(update.latestVersion()).isEqualTo(latestVersion);
        then(update.updateType()).isEqualTo(major);
        then(update.availableVersions()).isEqualTo(availableVersions);
        then(update.scope()).isEqualTo(compile);
    }

    @Test
    void shouldCreateRecordWithMinorUpdate_whenVersionsDiffer() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("1.1.0");
        var availableVersions = List.of(currentVersion, latestVersion);

        var update = new DependencyUpdate(
                dependency,
                "org.test",
                "lib",
                currentVersion,
                test,
                latestVersion,
                availableVersions,
                minor
        );

        then(update.updateType()).isEqualTo(minor);
        then(update.scope()).isEqualTo(test);
    }

    @Test
    void shouldCreateRecordWithPatchUpdate_whenVersionsDiffer() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("1.0.1");
        var availableVersions = List.of(currentVersion, latestVersion);

        var update = new DependencyUpdate(
                dependency,
                "org.test",
                "lib",
                currentVersion,
                runtime,
                latestVersion,
                availableVersions,
                patch
        );

        then(update.updateType()).isEqualTo(patch);
        then(update.scope()).isEqualTo(runtime);
    }
}
