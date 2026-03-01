package com.github.t1.mavendep.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Scope.compile;
import static com.github.t1.mavendep.domain.Scope.runtime;
import static com.github.t1.mavendep.domain.Scope.test;
import static com.github.t1.mavendep.domain.UpdateType.major;
import static com.github.t1.mavendep.domain.UpdateType.minor;
import static com.github.t1.mavendep.domain.UpdateType.none;
import static com.github.t1.mavendep.domain.UpdateType.patch;
import static org.assertj.core.api.BDDAssertions.then;

class DependencyUpdateTest {

    @Test
    void shouldCreateRecord_whenAllFieldsProvided() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("2.0.0");
        var availableVersions = List.of(currentVersion, latestVersion);

        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "lib", currentVersion, compile, null),
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
                new Dependency(dependency, "org.test", "lib", currentVersion, test, null),
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
                new Dependency(dependency, "org.test", "lib", currentVersion, runtime, null),
                latestVersion,
                availableVersions,
                patch
        );

        then(update.updateType()).isEqualTo(patch);
        then(update.scope()).isEqualTo(runtime);
    }

    @Test
    void shouldStoreVersionProperty() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("2.0.0");

        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "lib", currentVersion, compile, "lib.version"),
                latestVersion,
                List.of(),
                major
        );

        then(update.versionProperty()).isEqualTo("lib.version");
    }

    @Test void shouldHaveNullCommittedVersionByDefault() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                major
        );

        then(update.committedVersion()).isNull();
    }

    @Test void shouldHoldCommittedVersion() {
        var committed = Version.fromString("1.5.0");
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                major
        ).withCommittedVersion(committed);

        then(update.committedVersion()).isEqualTo(committed);
    }

    @Test
    void shouldBeChangeForUpgrade() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                major
        );

        then(update.isChange()).isTrue();
    }

    @Test
    void shouldBeChangeForDowngrade() {
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "lib", Version.fromString("2.0.0"), compile, null),
                Version.fromString("1.0.0"),
                List.of(),
                major
        );

        then(update.isChange()).isTrue();
    }

    @Test
    void shouldNotBeChangeWhenVersionsEqual() {
        var version = Version.fromString("1.0.0");
        var update = new DependencyUpdate(
                new Dependency(dependency, "com.example", "lib", version, compile, null),
                version,
                List.of(),
                none
        );

        then(update.isChange()).isFalse();
    }
}
