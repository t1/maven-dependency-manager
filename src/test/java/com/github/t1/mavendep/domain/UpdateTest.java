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
import static com.github.t1.mavendep.domain.VersionStatus.aheadOfLatestRelease;
import static com.github.t1.mavendep.domain.VersionStatus.noReleasedVersionAvailable;
import static com.github.t1.mavendep.domain.VersionStatus.upToDate;
import static com.github.t1.mavendep.domain.VersionStatus.upgradeAvailable;
import static org.assertj.core.api.BDDAssertions.then;

class UpdateTest {

    @Test
    void shouldCreateRecord_whenAllFieldsProvided() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("2.0.0");
        var availableVersions = List.of(currentVersion, latestVersion);

        var update = new Update(
                new Dependency(dependency, "com.example", "lib", currentVersion, compile, null),
                latestVersion,
                availableVersions,
                major
        );

        then(update.groupId()).isEqualTo("com.example");
        then(update.artifactId()).isEqualTo("lib");
        then(update.declaredVersion()).isEqualTo(currentVersion);
        then(update.currentVersion()).isEqualTo(currentVersion);
        then(update.latestVersion()).isEqualTo(latestVersion);
        then(update.updateType()).isEqualTo(major);
        then(update.versionStatus()).isEqualTo(upgradeAvailable);
        then(update.availableVersions()).isEqualTo(availableVersions);
        then(update.scope()).isEqualTo(compile);
    }

    @Test
    void shouldCreateRecordWithMinorUpdate_whenVersionsDiffer() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("1.1.0");
        var availableVersions = List.of(currentVersion, latestVersion);

        var update = new Update(
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

        var update = new Update(
                new Dependency(dependency, "org.test", "lib", currentVersion, runtime, null),
                latestVersion,
                availableVersions,
                patch
        );

        then(update.updateType()).isEqualTo(patch);
        then(update.scope()).isEqualTo(runtime);
    }

    @Test
    void shouldStoreEffectiveVersionSeparateFromDeclaredVersion() {
        var declaredVersion = Version.fromString("1.0.0");
        var effectiveVersion = Version.fromString("1.5.0");
        var latestVersion = Version.fromString("2.0.0");

        var update = new Update(
                new Dependency(dependency, "com.example", "lib", declaredVersion, compile, null),
                effectiveVersion,
                latestVersion,
                List.of(),
                major
        );

        then(update.declaredVersion()).isEqualTo(declaredVersion);
        then(update.currentVersion()).isEqualTo(effectiveVersion);
    }

    @Test
    void shouldStoreVersionProperty() {
        var currentVersion = Version.fromString("1.0.0");
        var latestVersion = Version.fromString("2.0.0");

        var update = new Update(
                new Dependency(dependency, "com.example", "lib", currentVersion, compile, "lib.version"),
                latestVersion,
                List.of(),
                major
        );

        then(update.versionProperty()).isEqualTo("lib.version");
    }

    @Test void shouldHaveNullCommittedVersionByDefault() {
        var update = new Update(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                major
        );

        then(update.committedVersion()).isNull();
    }

    @Test void shouldHoldCommittedVersion() {
        var committed = Version.fromString("1.5.0");
        var update = new Update(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                major
        ).withCommittedVersion(committed);

        then(update.committedVersion()).isEqualTo(committed);
    }

    @Test
    void shouldBeChangeForUpgrade() {
        var update = new Update(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0"), compile, null),
                Version.fromString("2.0.0"),
                List.of(),
                major
        );

        then(update.isChange()).isTrue();
    }

    @Test
    void shouldNotBeChangeForAheadOfLatestRelease() {
        var update = new Update(
                new Dependency(dependency, "com.example", "lib", Version.fromString("2.0.0"), compile, null),
                Version.fromString("1.0.0"),
                List.of(),
                none
        );

        then(update.versionStatus()).isEqualTo(aheadOfLatestRelease);
        then(update.isChange()).isFalse();
        then(update.isUpdateAvailable()).isFalse();
    }

    @Test
    void shouldNotBeChangeWhenVersionsEqual() {
        var version = Version.fromString("1.0.0");
        var update = new Update(
                new Dependency(dependency, "com.example", "lib", version, compile, null),
                version,
                List.of(),
                none
        );

        then(update.versionStatus()).isEqualTo(upToDate);
        then(update.isChange()).isFalse();
    }

    @Test
    void shouldTreatSnapshotOnReleasedLineAsUpgradeAvailable() {
        var update = new Update(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0-SNAPSHOT"), compile, null),
                Version.fromString("1.0.0"),
                List.of(),
                patch
        );

        then(update.versionStatus()).isEqualTo(upgradeAvailable);
        then(update.isUpdateAvailable()).isTrue();
    }

    @Test
    void shouldDetectMissingReleasedVersion() {
        var update = new Update(
                new Dependency(dependency, "com.example", "lib", Version.fromString("1.0.0-SNAPSHOT"), compile, null),
                null,
                List.of(),
                none
        );

        then(update.versionStatus()).isEqualTo(noReleasedVersionAvailable);
        then(update.isUpdateAvailable()).isFalse();
    }
}
